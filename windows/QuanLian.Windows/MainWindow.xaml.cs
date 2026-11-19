using System.Collections.ObjectModel;
using System.Windows;
using QuanLian.Windows.Models;
using QuanLian.Windows.Services;

namespace QuanLian.Windows;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<DeviceInfo> _devices = new();
    private LanDiscoveryService? _discovery;
    private readonly string _deviceId = "windows-" + Guid.NewGuid().ToString("N");

    public MainWindow()
    {
        InitializeComponent();
        DevicesList.ItemsSource = _devices;
        DeviceIdentityText.Text = "设备：" + Environment.MachineName;
        Loaded += OnLoaded;
        Closed += OnClosed;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        try
        {
            _discovery = new LanDiscoveryService(_deviceId);
            _discovery.DeviceDiscovered += (_, device) => Dispatcher.Invoke(() =>
            {
                var index = -1;
                for (var i = 0; i < _devices.Count; i++)
                    if (_devices[i].Id == device.Id) { index = i; break; }
                if (index >= 0) _devices[index] = device; else _devices.Add(device);
                DeviceCount.Text = $"发现 {_devices.Count} 台设备";
                EmptyDevices.Text = "";
                ReceiveStatus.Text = "局域网设备发现已启动；文件接收服务正在开发中。";
            });
            ReceiveStatus.Text = "设备发现已启动；正在搜索同一局域网内的小李全联设备。";
        }
        catch (Exception ex)
        {
            ReceiveStatus.Text = "局域网服务启动失败：" + ex.Message;
        }
    }

    private async void OnClosed(object? sender, EventArgs e)
    {
        if (_discovery is not null) await _discovery.DisposeAsync();
    }
}
