using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using QuanLian.Windows.Models;

namespace QuanLian.Windows.Services;

/// <summary>
/// Implements the Android QuanLian LAN announcement envelope (app=QUANLIAN, v=3,
/// UDP 38889, multicast 224.0.0.167 plus subnet broadcast).
/// TCP/TLS identity verification is deliberately a separate stage.
/// </summary>
public sealed class LanDiscoveryService : IAsyncDisposable
{
    public const int DiscoveryPort = 38889;
    public const int DefaultTransferPort = 38890;
    private static readonly IPAddress MulticastGroup = IPAddress.Parse("224.0.0.167");
    private readonly UdpClient _udp = new(AddressFamily.InterNetwork);
    private readonly CancellationTokenSource _stop = new();
    private readonly string _ownId;
    public event EventHandler<DeviceInfo>? DeviceDiscovered;

    public LanDiscoveryService(string ownId)
    {
        _ownId = ownId;
        _udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        _udp.Client.Bind(new IPEndPoint(IPAddress.Any, DiscoveryPort));
        try { _udp.JoinMulticastGroup(MulticastGroup); } catch (SocketException) { }
        _ = ReceiveLoop(_stop.Token);
    }

    private async Task ReceiveLoop(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var packet = await _udp.ReceiveAsync(token);
                using var json = JsonDocument.Parse(packet.Buffer);
                var root = json.RootElement;
                if (!root.TryGetProperty("app", out var app) || app.GetString() != "QUANLIAN") continue;
                if (!root.TryGetProperty("v", out var version) || version.GetInt32() != 3) continue;
                var id = root.GetProperty("id").GetString() ?? "";
                var name = root.GetProperty("name").GetString() ?? "";
                var fp = root.GetProperty("fp").GetString() ?? "";
                var port = root.GetProperty("port").GetInt32();
                if (id.Length is < 8 or > 128 || name.Length is < 1 or > 128 ||
                    !System.Text.RegularExpressions.Regex.IsMatch(fp, "^[0-9a-fA-F]{64}$") ||
                    port is < 1 or > 65535 || id == _ownId) continue;
                DeviceDiscovered?.Invoke(this, new DeviceInfo(id, name, packet.RemoteEndPoint.Address.ToString(), port, fp.ToLowerInvariant(), DateTime.UtcNow));
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (Exception) when (!token.IsCancellationRequested) { await Task.Delay(150, token); }
        }
    }

    public async Task AnnounceAsync(string id, string name, int tcpPort, string certificateFingerprint, CancellationToken token)
    {
        var payload = JsonSerializer.SerializeToUtf8Bytes(new
        {
            app = "QUANLIAN", v = 3, id, name, port = tcpPort, fp = certificateFingerprint
        });
        using var sender = new UdpClient(AddressFamily.InterNetwork) { EnableBroadcast = true };
        try { await sender.SendAsync(payload, new IPEndPoint(MulticastGroup, DiscoveryPort), token); } catch (SocketException) { }
        foreach (var address in GetBroadcastAddresses())
        {
            try { await sender.SendAsync(payload, new IPEndPoint(address, DiscoveryPort), token); } catch (SocketException) { }
        }
    }

    private static IEnumerable<IPAddress> GetBroadcastAddresses()
    {
        foreach (var nic in System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != System.Net.NetworkInformation.OperationalStatus.Up) continue;
            var props = nic.GetIPProperties();
            foreach (var uni in props.UnicastAddresses)
            {
                if (uni.Address.AddressFamily != AddressFamily.InterNetwork || uni.IPv4Mask is null) continue;
                var ip = uni.Address.GetAddressBytes(); var mask = uni.IPv4Mask.GetAddressBytes();
                var broadcast = new byte[4];
                for (var i = 0; i < 4; i++) broadcast[i] = (byte)(ip[i] | ~mask[i]);
                yield return new IPAddress(broadcast);
            }
        }
    }

    public async ValueTask DisposeAsync()
    {
        _stop.Cancel();
        _udp.Dispose();
        await Task.CompletedTask;
        _stop.Dispose();
    }
}
