using System.Collections.ObjectModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Microsoft.Win32;
using QuanLian.Windows.Models;
using QuanLian.Windows.Services;

namespace QuanLian.Windows;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<DeviceInfo> _devices = new();
    private LanDiscoveryService? _discovery;
    private QuanLianTransferService? _transfer;
    private readonly string _deviceId = "windows-" + Guid.NewGuid().ToString("N");
    private readonly CancellationTokenSource _announceStop = new();
    private Task? _announceTask;

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
            _transfer = new QuanLianTransferService(_deviceId, Environment.MachineName);
            _transfer.StatusChanged += message => Dispatcher.Invoke(() => ReceiveStatus.Text = message);
            _transfer.IncomingRequested += AskToReceiveAsync;
            _transfer.Start();

            _discovery = new LanDiscoveryService(_deviceId);
            _discovery.DeviceDiscovered += device => Dispatcher.Invoke(() =>
            {
                var index = -1;
                for (var i = 0; i < _devices.Count; i++)
                    if (_devices[i].Id == device.Id) { index = i; break; }
                if (index >= 0) _devices[index] = device; else _devices.Add(device);
                DeviceCount.Text = $"发现 {_devices.Count} 台设备";
                EmptyDevices.Text = "";
            });
            _announceTask = AnnounceLoopAsync(_announceStop.Token);
            ReceiveStatus.Text = "接收服务已启动，正在搜索同一局域网内的小李全联设备。";
        }
        catch (Exception ex)
        {
            ReceiveStatus.Text = "局域网服务启动失败：" + ex.Message;
            EmptyDevices.Text = "设备搜索暂不可用，请检查 Windows 防火墙和网络权限。";
        }
    }

    private async Task AnnounceLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                if (_discovery is not null && _transfer is not null)
                    await _discovery.AnnounceAsync(_deviceId, Environment.MachineName, 38890, _transfer.Fingerprint, token);
                await Task.Delay(2000, token);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { Dispatcher.Invoke(() => ReceiveStatus.Text = "设备广播异常：" + ex.Message); }
        }
    }

    private Task<bool> AskToReceiveAsync(IncomingTransfer request)
    {
        return Dispatcher.InvokeAsync(() =>
        {
            var details = string.Join(Environment.NewLine, request.Files.Select(f => $"• {f.Name}  ({FormatSize(f.Size)})"));
            var result = MessageBox.Show(this,
                $"设备“{request.Sender}”请求发送 {request.Files.Count} 个文件：\n\n{details}\n\n是否接收？",
                "小李全联 · 收到文件请求", MessageBoxButton.YesNo, MessageBoxImage.Question);
            return result == MessageBoxResult.Yes;
        }).Task;
    }

    private static string FormatSize(long size) => size < 1024 * 1024
        ? $"{size / 1024d:0.0} KB" : size < 1024L * 1024 * 1024
            ? $"{size / 1024d / 1024:0.0} MB" : $"{size / 1024d / 1024 / 1024:0.00} GB";

    private async void SendFiles_Click(object sender, RoutedEventArgs e)
    {
        if (_transfer is null)
        {
            MessageBox.Show(this, "文件传输服务尚未启动。", "小李全联", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        var kind = (sender as Button)?.Tag?.ToString() ?? "file";
        var paths = new List<string>();
        switch (kind)
        {
            case "image":
            {
                var dialog = new OpenFileDialog { Title = "选择图片", Multiselect = true, Filter = "图片文件|*.jpg;*.jpeg;*.png;*.gif;*.bmp;*.webp;*.heic|所有文件|*.*" };
                if (dialog.ShowDialog(this) != true) return;
                paths.AddRange(dialog.FileNames);
                break;
            }
            case "media":
            {
                var dialog = new OpenFileDialog { Title = "选择音频或视频", Multiselect = true, Filter = "媒体文件|*.mp4;*.mkv;*.mov;*.avi;*.mp3;*.wav;*.flac;*.m4a;*.aac|所有文件|*.*" };
                if (dialog.ShowDialog(this) != true) return;
                paths.AddRange(dialog.FileNames);
                break;
            }
            case "folder":
            {
                var dialog = new OpenFolderDialog { Title = "选择要发送的文件夹", Multiselect = false };
                if (dialog.ShowDialog(this) != true || string.IsNullOrWhiteSpace(dialog.FolderName)) return;
                try { paths.AddRange(Directory.EnumerateFiles(dialog.FolderName, "*", SearchOption.AllDirectories)); }
                catch (Exception ex) { MessageBox.Show(this, "读取文件夹失败：" + ex.Message, "小李全联", MessageBoxButton.OK, MessageBoxImage.Error); return; }
                if (paths.Count == 0) { MessageBox.Show(this, "这个文件夹里面没有可发送的文件。"); return; }
                if (paths.Count > 500) { MessageBox.Show(this, "单次最多发送 500 个文件，请拆分后发送。"); return; }
                break;
            }
            case "text":
            {
                var editor = new Window { Title = "发送文本", Owner = this, Width = 620, Height = 430, WindowStartupLocation = WindowStartupLocation.CenterOwner, Background = new SolidColorBrush(Color.FromRgb(244, 248, 255)) };
                var layout = new DockPanel { Margin = new Thickness(18) };
                var textBox = new TextBox { AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, FontSize = 15, Padding = new Thickness(12), MinHeight = 280 };
                var sendButton = new Button { Content = "继续选择接收设备", Height = 42, Margin = new Thickness(0, 12, 0, 0), IsDefault = true, Background = new SolidColorBrush(Color.FromRgb(40, 120, 232)), Foreground = Brushes.White };
                sendButton.Click += (_, _) => { if (string.IsNullOrWhiteSpace(textBox.Text)) { MessageBox.Show(editor, "请输入要发送的文本。"); return; } editor.DialogResult = true; };
                layout.Children.Add(sendButton);
                layout.Children.Add(textBox);
                editor.Content = layout;
                if (editor.ShowDialog() != true) return;
                var textDirectory = Path.Combine(Path.GetTempPath(), "QuanLianText");
                Directory.CreateDirectory(textDirectory);
                var textPath = Path.Combine(textDirectory, $"文本_{DateTime.Now:yyyyMMdd_HHmmss}_{Guid.NewGuid():N}.txt");
                await File.WriteAllTextAsync(textPath, textBox.Text, new System.Text.UTF8Encoding(false));
                paths.Add(textPath);
                break;
            }
            case "app":
            {
                var dialog = new OpenFileDialog { Title = "选择应用安装包", Multiselect = true, Filter = "Windows 应用安装包|*.exe;*.msi;*.msix;*.appx;*.msixbundle;*.appxbundle|所有文件|*.*" };
                if (dialog.ShowDialog(this) != true) return;
                paths.AddRange(dialog.FileNames);
                break;
            }
            default:
            {
                var dialog = new OpenFileDialog { Title = "选择要发送的文件", Multiselect = true, CheckFileExists = true };
                if (dialog.ShowDialog(this) != true) return;
                paths.AddRange(dialog.FileNames);
                break;
            }
        }

        if (paths.Count == 0) return;
        if (_devices.Count == 0)
        {
            MessageBox.Show(this, "暂未发现可用的接收设备，请确认双方处于同一局域网。", "小李全联", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        var picker = new Window
        {
            Title = "选择接收设备", Owner = this, Width = 460, Height = 420,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = new SolidColorBrush(Color.FromRgb(244, 248, 255))
        };
        var panel = new DockPanel { Margin = new Thickness(18) };
        var list = new ListBox { ItemsSource = _devices, DisplayMemberPath = nameof(DeviceInfo.Name), Margin = new Thickness(0, 12, 0, 12) };
        var send = new Button { Content = "发送文件", Height = 42, IsDefault = true, Background = new SolidColorBrush(Color.FromRgb(40, 120, 232)), Foreground = Brushes.White };
        send.Click += (_, _) => { if (list.SelectedItem is DeviceInfo) picker.DialogResult = true; else MessageBox.Show(picker, "请先选择接收设备。"); };
        panel.Children.Add(send);
        panel.Children.Add(list);
        picker.Content = panel;
        if (picker.ShowDialog() != true || list.SelectedItem is not DeviceInfo target) return;

        try
        {
            ReceiveStatus.Text = $"正在向 {target.Name} 发送 {paths.Count} 个文件…";
            await _transfer.SendFilesAsync(paths, target.Host, target.Port, target.Fingerprint, target.Name);
            MessageBox.Show(this, $"已成功发送 {paths.Count} 个文件。", "发送完成", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, "发送失败：" + ex.Message, "小李全联", MessageBoxButton.OK, MessageBoxImage.Error);
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
        _announceStop.Cancel();
        if (_discovery is not null) await _discovery.DisposeAsync();
        if (_transfer is not null) await _transfer.DisposeAsync();
        _announceStop.Dispose();
    }
}
