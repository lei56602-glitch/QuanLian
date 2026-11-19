using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
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
            _discovery.DeviceDiscovered += device => Dispatcher.Invoke(() =>
            {
                var index = -1;
                for (var i = 0; i < _devices.Count; i++)
                    if (_devices[i].Id == device.Id) { index = i; break; }
                if (index >= 0) _devices[index] = device; else _devices.Add(device);
                DeviceCount.Text = $"发现 {_devices.Count} 台设备";
                EmptyDevices.Text = "";
                ReceiveStatus.Text = "已发现局域网设备。当前版本的文件接收服务尚未接入。";
            });
            ReceiveStatus.Text = "设备发现已启动，正在搜索同一局域网内的小李全联设备。";
        }
        catch (Exception ex)
        {
            ReceiveStatus.Text = "局域网服务启动失败：" + ex.Message;
            EmptyDevices.Text = "设备搜索暂不可用，请检查 Windows 防火墙和网络权限。";
        }
    }

    private void ShowPage(StackPanel page, Button selected)
    {
        ReceivePage.Visibility = page == ReceivePage ? Visibility.Visible : Visibility.Collapsed;
        SendPage.Visibility = page == SendPage ? Visibility.Visible : Visibility.Collapsed;
        OtherPage.Visibility = page == OtherPage ? Visibility.Visible : Visibility.Collapsed;

        foreach (var button in new[] { ReceiveNav, SendNav, HistoryNav, SettingsNav })
        {
            button.Background = Brushes.Transparent;
            button.Foreground = new SolidColorBrush(Color.FromRgb(96, 112, 140));
        }
        selected.Background = new SolidColorBrush(Color.FromRgb(216, 231, 255));
        selected.Foreground = new SolidColorBrush(Color.FromRgb(40, 120, 232));
    }

    private void ReceiveNav_Click(object sender, RoutedEventArgs e) => ShowPage(ReceivePage, ReceiveNav);
    private void SendNav_Click(object sender, RoutedEventArgs e) => ShowPage(SendPage, SendNav);

    private void HistoryNav_Click(object sender, RoutedEventArgs e)
    {
        OtherPageTitle.Text = "传输记录";
        ShowPage(OtherPage, HistoryNav);
    }

    private void SettingsNav_Click(object sender, RoutedEventArgs e)
    {
        OtherPageTitle.Text = "设置";
        ShowPage(OtherPage, SettingsNav);
    }

    private async void OnClosed(object? sender, EventArgs e)
    {
        if (_discovery is not null) await _discovery.DisposeAsync();
    }
}