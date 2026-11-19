using System.Buffers.Binary;
using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;

namespace QuanLian.Windows.Services;

public sealed record IncomingFile(string Name, long Size, string Sha256);
public sealed record IncomingTransfer(string Sender, string SenderId, IReadOnlyList<IncomingFile> Files);

/// <summary>Windows implementation of the Android QUANLIAN4 TLS/TCP wire protocol.</summary>
public sealed class QuanLianTransferService : IAsyncDisposable
{
    private const int Port = 38890;
    private const int ChunkSize = 4 * 1024 * 1024;
    private const long AckWindow = 16L * 1024 * 1024;
    private readonly string _deviceId;
    private readonly string _deviceName;
    private readonly X509Certificate2 _certificate;
    private readonly TcpListener _listener = new(IPAddress.Any, Port);
    private readonly CancellationTokenSource _stop = new();
    private readonly SemaphoreSlim _sendLock = new(1, 1);
    private Task? _acceptLoop;

    public string Fingerprint { get; }
    public event Func<IncomingTransfer, Task<bool>>? IncomingRequested;
    public event Action<string>? StatusChanged;

    public QuanLianTransferService(string deviceId, string deviceName)
    {
        _deviceId = deviceId;
        _deviceName = deviceName;
        _certificate = LoadOrCreateCertificate();
        Fingerprint = Convert.ToHexString(SHA256.HashData(_certificate.RawData)).ToLowerInvariant();
    }

    public void Start()
    {
        _listener.Start(32);
        _acceptLoop = AcceptLoopAsync(_stop.Token);
    }

    private async Task AcceptLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var client = await _listener.AcceptTcpClientAsync(token);
                _ = Task.Run(() => HandleClientAsync(client, token), CancellationToken.None);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (Exception ex) { StatusChanged?.Invoke("接收服务异常：" + ex.Message); await Task.Delay(300, token); }
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken token)
    {
        using (client)
        using (var ssl = new SslStream(client.GetStream(), false))
        {
            try
            {
                await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                {
                    ServerCertificate = _certificate,
                    EnabledSslProtocols = SslProtocols.Tls12,
                    ClientCertificateRequired = false,
                    CertificateRevocationCheckMode = X509RevocationMode.NoCheck
                }, token);
                var protocol = await JavaWire.ReadUtfAsync(ssl, token);
                if (protocol == "QUANLIAN_DISCOVERY_PROBE")
                {
                    await JavaWire.WriteUtfAsync(ssl, "QUANLIAN_DISCOVERY_OK", token);
                    return;
                }
                if (protocol != "QUANLIAN4") throw new InvalidDataException("不支持的传输协议：" + protocol);

                var sender = await JavaWire.ReadUtfAsync(ssl, token);
                var senderId = await JavaWire.ReadUtfAsync(ssl, token);
                var count = await JavaWire.ReadInt32Async(ssl, token);
                if (string.IsNullOrWhiteSpace(sender) || sender.Length > 128 ||
                    string.IsNullOrWhiteSpace(senderId) || senderId.Length > 128 || count is < 1 or > 500)
                    throw new InvalidDataException("发送设备或文件数量无效");

                var files = new List<IncomingFile>(count);
                long total = 0;
                for (var i = 0; i < count; i++)
                {
                    var name = await JavaWire.ReadUtfAsync(ssl, token);
                    var size = await JavaWire.ReadInt64Async(ssl, token);
                    var hash = await JavaWire.ReadUtfAsync(ssl, token);
                    if (string.IsNullOrWhiteSpace(name) || name.Length > 255 || size < 0 ||
                        !System.Text.RegularExpressions.Regex.IsMatch(hash, "^[0-9a-fA-F]{64}$") ||
                        total > long.MaxValue - size) throw new InvalidDataException("文件清单无效");
                    total += size;
                    files.Add(new IncomingFile(name, size, hash.ToLowerInvariant()));
                }

                var request = new IncomingTransfer(sender, senderId, files);
                var handlers = IncomingRequested;
                var accepted = handlers is not null && await handlers(request);
                await JavaWire.WriteBooleanAsync(ssl, accepted, token);
                if (!accepted) return;

                var directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "小李全联");
                Directory.CreateDirectory(directory);
                for (var i = 0; i < files.Count; i++)
                {
                    var file = files[i];
                    var safeName = SanitizeFileName(file.Name);
                    var destination = UniquePath(directory, safeName);
                    var senderKey = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(senderId))).ToLowerInvariant()[..16];
                    var temp = Path.Combine(directory, $".quanlian_{senderKey}_{file.Sha256}.part");
                    if (File.Exists(temp) && new FileInfo(temp).Length > file.Size) File.Delete(temp);
                    var offset = File.Exists(temp) ? new FileInfo(temp).Length : 0L;
                    using var sha = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
                    if (offset > 0)
                    {
                        await using var prior = new FileStream(temp, FileMode.Open, FileAccess.Read, FileShare.Read, ChunkSize, FileOptions.Asynchronous | FileOptions.SequentialScan);
                        var priorBuffer = new byte[ChunkSize];
                        int priorRead;
                        while ((priorRead = await prior.ReadAsync(priorBuffer, token)) > 0) sha.AppendData(priorBuffer, 0, priorRead);
                    }
                    await JavaWire.WriteInt64Async(ssl, offset, token);
                    await using (var output = new FileStream(temp, offset == 0 ? FileMode.Create : FileMode.Append, FileAccess.Write, FileShare.None, ChunkSize,
                                     FileOptions.Asynchronous | FileOptions.SequentialScan))
                    {
                        long remaining = file.Size - offset;
                        while (remaining > 0)
                        {
                            long batch = 0;
                            while (remaining > 0 && batch < AckWindow)
                            {
                                var length = await JavaWire.ReadInt32Async(ssl, token);
                                if (length is < 1 or > ChunkSize || length > remaining)
                                    throw new InvalidDataException("数据块长度无效");
                                var buffer = new byte[length];
                                await JavaWire.ReadExactlyAsync(ssl, buffer, token);
                                await output.WriteAsync(buffer, token);
                                sha.AppendData(buffer);
                                remaining -= length;
                                batch += length;
                            }
                            await output.FlushAsync(token);
                            output.Flush(true);
                            await JavaWire.WriteInt64Async(ssl, file.Size - remaining, token);
                        }
                        output.Flush(true);
                    }
                    var actualHash = Convert.ToHexString(sha.GetHashAndReset()).ToLowerInvariant();
                    if (!CryptographicOperations.FixedTimeEquals(Convert.FromHexString(actualHash), Convert.FromHexString(file.Sha256)))
                    {
                        File.Delete(temp);
                        throw new InvalidDataException("文件 SHA-256 校验失败：" + safeName);
                    }
                    File.Move(temp, destination);
                    StatusChanged?.Invoke($"已接收 {i + 1}/{files.Count}：{safeName}");
                }
                await JavaWire.WriteBooleanAsync(ssl, true, token);
                StatusChanged?.Invoke($"接收完成：来自 {sender}，共 {files.Count} 个文件");
            }
            catch (OperationCanceledException) { }
            catch (Exception ex) { StatusChanged?.Invoke("接收失败：" + ex.Message); }
        }
    }

    public async Task SendFilesAsync(IReadOnlyList<string> paths, string host, int port, string expectedFingerprint, string peerName, CancellationToken token = default)
    {
        if (paths.Count is < 1 or > 500) throw new ArgumentException("请选择 1 到 500 个文件。", nameof(paths));
        await _sendLock.WaitAsync(token);
        try
        {
            var files = new List<(string Path, string Name, long Size, string Hash)>();
            foreach (var path in paths)
            {
                var info = new FileInfo(path);
                if (!info.Exists) throw new FileNotFoundException("待发送文件不存在", path);
                await using var input = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read, ChunkSize, FileOptions.Asynchronous | FileOptions.SequentialScan);
                using var sha = SHA256.Create();
                var hash = Convert.ToHexString(await sha.ComputeHashAsync(input, token)).ToLowerInvariant();
                files.Add((path, info.Name, info.Length, hash));
            }

            using var tcp = new TcpClient();
            await tcp.ConnectAsync(host, port, token);
            using var ssl = new SslStream(tcp.GetStream(), false, (_, certificate, _, errors) =>
            {
                if (certificate is null) return false;
                var actual = Convert.ToHexString(SHA256.HashData(certificate.GetRawCertData())).ToLowerInvariant();
                return string.Equals(actual, expectedFingerprint, StringComparison.OrdinalIgnoreCase);
            });
            await ssl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
            {
                TargetHost = "QuanLian",
                EnabledSslProtocols = SslProtocols.Tls12,
                CertificateRevocationCheckMode = X509RevocationMode.NoCheck
            }, token);

            await JavaWire.WriteUtfAsync(ssl, "QUANLIAN4", token);
            await JavaWire.WriteUtfAsync(ssl, _deviceName, token);
            await JavaWire.WriteUtfAsync(ssl, _deviceId, token);
            await JavaWire.WriteInt32Async(ssl, files.Count, token);
            foreach (var file in files)
            {
                await JavaWire.WriteUtfAsync(ssl, file.Name, token);
                await JavaWire.WriteInt64Async(ssl, file.Size, token);
                await JavaWire.WriteUtfAsync(ssl, file.Hash, token);
            }
            if (!await JavaWire.ReadBooleanAsync(ssl, token)) throw new IOException("对方拒绝接收文件。");

            long sent = 0;
            foreach (var file in files)
            {
                var offset = await JavaWire.ReadInt64Async(ssl, token);
                if (offset < 0 || offset > file.Size) throw new InvalidDataException("接收端续传位置无效。");
                await using var input = new FileStream(file.Path, FileMode.Open, FileAccess.Read, FileShare.Read, ChunkSize, FileOptions.Asynchronous | FileOptions.SequentialScan);
                input.Seek(offset, SeekOrigin.Begin);
                var buffer = new byte[ChunkSize];
                long remaining = file.Size - offset;
                while (remaining > 0)
                {
                    long batch = 0;
                    while (remaining > 0 && batch < AckWindow)
                    {
                        var wanted = (int)Math.Min(buffer.Length, remaining);
                        var read = await input.ReadAsync(buffer.AsMemory(0, wanted), token);
                        if (read == 0) throw new EndOfStreamException("本地文件读取不完整。");
                        await JavaWire.WriteInt32Async(ssl, read, token);
                        await ssl.WriteAsync(buffer.AsMemory(0, read), token);
                        remaining -= read;
                        batch += read;
                        sent += read;
                    }
                    await ssl.FlushAsync(token);
                    var ack = await JavaWire.ReadInt64Async(ssl, token);
                    if (ack != file.Size - remaining) throw new InvalidDataException("接收端批量确认位置不匹配。");
                    StatusChanged?.Invoke($"正在发送到 {peerName}：{sent:N0} 字节");
                }
            }
            if (!await JavaWire.ReadBooleanAsync(ssl, token)) throw new IOException("接收端未确认文件校验完成。");
            StatusChanged?.Invoke($"发送完成：{files.Count} 个文件已发送到 {peerName}");
        }
        finally { _sendLock.Release(); }
    }

    private static X509Certificate2 LoadOrCreateCertificate()
    {
        const string subject = "CN=QuanLian Windows Device";
        using var store = new X509Store(StoreName.My, StoreLocation.CurrentUser);
        store.Open(OpenFlags.ReadWrite);
        var existing = store.Certificates.Find(X509FindType.FindBySubjectDistinguishedName, subject, false)
            .OfType<X509Certificate2>().FirstOrDefault(c => c.HasPrivateKey && c.NotAfter > DateTime.UtcNow.AddDays(7));
        if (existing is not null) return new X509Certificate2(existing);
        using var rsa = RSA.Create(2048);
        var request = new CertificateRequest(subject, rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, true));
        var eku = new OidCollection { new Oid("1.3.6.1.5.5.7.3.1") };
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(eku, false));
        var cert = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddDays(365));
        store.Add(cert);
        return new X509Certificate2(cert);
    }

    private static string SanitizeFileName(string name)
    {
        var invalid = Path.GetInvalidFileNameChars();
        var cleaned = new string(name.Select(c => invalid.Contains(c) || c is '/' or '\\' ? '_' : c).ToArray()).Trim();
        return string.IsNullOrWhiteSpace(cleaned) || cleaned is "." or ".." ? "received_file" : cleaned;
    }

    private static string UniquePath(string directory, string name)
    {
        var path = Path.Combine(directory, name);
        if (!File.Exists(path)) return path;
        var stem = Path.GetFileNameWithoutExtension(name);
        var ext = Path.GetExtension(name);
        for (var i = 1; ; i++)
        {
            path = Path.Combine(directory, $"{stem} ({i}){ext}");
            if (!File.Exists(path)) return path;
        }
    }

    public async ValueTask DisposeAsync()
    {
        _stop.Cancel();
        _listener.Stop();
        if (_acceptLoop is not null) try { await _acceptLoop; } catch { }
        _sendLock.Dispose();
        _stop.Dispose();
        _certificate.Dispose();
    }
}

internal static class JavaWire
{
    public static async Task ReadExactlyAsync(Stream stream, Memory<byte> buffer, CancellationToken token)
    {
        var offset = 0;
        while (offset < buffer.Length)
        {
            var n = await stream.ReadAsync(buffer[offset..], token);
            if (n == 0) throw new EndOfStreamException();
            offset += n;
        }
    }

    public static async Task<int> ReadInt32Async(Stream s, CancellationToken t) { var b = new byte[4]; await ReadExactlyAsync(s, b, t); return BinaryPrimitives.ReadInt32BigEndian(b); }
    public static async Task<long> ReadInt64Async(Stream s, CancellationToken t) { var b = new byte[8]; await ReadExactlyAsync(s, b, t); return BinaryPrimitives.ReadInt64BigEndian(b); }
    public static async Task<bool> ReadBooleanAsync(Stream s, CancellationToken t) { var b = new byte[1]; await ReadExactlyAsync(s, b, t); return b[0] != 0; }
    public static async Task WriteInt32Async(Stream s, int value, CancellationToken t) { var b = new byte[4]; BinaryPrimitives.WriteInt32BigEndian(b, value); await s.WriteAsync(b, t); }
    public static async Task WriteInt64Async(Stream s, long value, CancellationToken t) { var b = new byte[8]; BinaryPrimitives.WriteInt64BigEndian(b, value); await s.WriteAsync(b, t); }
    public static async Task WriteBooleanAsync(Stream s, bool value, CancellationToken t) => await s.WriteAsync(new[] { value ? (byte)1 : (byte)0 }, t);

    public static async Task<string> ReadUtfAsync(Stream s, CancellationToken t)
    {
        var header = new byte[2]; await ReadExactlyAsync(s, header, t);
        var length = BinaryPrimitives.ReadUInt16BigEndian(header);
        var bytes = new byte[length]; await ReadExactlyAsync(s, bytes, t);
        var chars = new List<char>(length);
        for (var i = 0; i < bytes.Length;)
        {
            var c = bytes[i++];
            if ((c & 0x80) == 0) chars.Add((char)c);
            else if ((c & 0xE0) == 0xC0)
            {
                if (i >= bytes.Length) throw new InvalidDataException("Malformed modified UTF-8");
                chars.Add((char)(((c & 0x1F) << 6) | (bytes[i++] & 0x3F)));
            }
            else if ((c & 0xF0) == 0xE0)
            {
                if (i + 1 >= bytes.Length) throw new InvalidDataException("Malformed modified UTF-8");
                chars.Add((char)(((c & 0x0F) << 12) | ((bytes[i++] & 0x3F) << 6) | (bytes[i++] & 0x3F)));
            }
            else throw new InvalidDataException("Malformed modified UTF-8");
        }
        return new string(chars.ToArray());
    }

    public static async Task WriteUtfAsync(Stream s, string value, CancellationToken t)
    {
        using var buffer = new MemoryStream();
        foreach (var ch in value)
        {
            if (ch >= (char)1 && ch <= (char)127) buffer.WriteByte((byte)ch);
            else if (ch <= (char)2047) { buffer.WriteByte((byte)(0xC0 | ((ch >> 6) & 0x1F))); buffer.WriteByte((byte)(0x80 | (ch & 0x3F))); }
            else { buffer.WriteByte((byte)(0xE0 | ((ch >> 12) & 0x0F))); buffer.WriteByte((byte)(0x80 | ((ch >> 6) & 0x3F))); buffer.WriteByte((byte)(0x80 | (ch & 0x3F))); }
        }
        if (buffer.Length > ushort.MaxValue) throw new InvalidDataException("Java UTF field exceeds 65535 bytes");
        var header = new byte[2]; BinaryPrimitives.WriteUInt16BigEndian(header, (ushort)buffer.Length);
        await s.WriteAsync(header, t);
        buffer.Position = 0;
        await buffer.CopyToAsync(s, t);
    }
}
