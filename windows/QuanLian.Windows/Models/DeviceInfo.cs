namespace QuanLian.Windows.Models;
public sealed record DeviceInfo(string Id, string Name, string Host, int Port, string Fingerprint, DateTime LastSeenUtc);
