using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows.Browser;
using System.Windows.Controls;
using System.Windows.Threading;

namespace SilverlightMalaRIA
{
    public partial class MainPage : UserControl
    {

        private readonly Socket _socket;
        private readonly HtmlDocument _document;

        private const int port = 4502;
        private const string hostname = "localhost";


        public MainPage()
        {
            InitializeComponent();
            UIThread.Dispatcher = Dispatcher;
            _document = HtmlPage.Document;
            try
            {
                Log("Connecting back to malaria server (" + hostname + ":" + port + ")...");
                _socket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
                var args = new SocketAsyncEventArgs { RemoteEndPoint = new DnsEndPoint(hostname, port, AddressFamily.InterNetwork) };
                args.Completed += OnConnected;
                _socket.ConnectAsync(args);
            }
            catch (Exception ex)
            {
                Log(ex.Message);
            }
        }
        public void OnConnected(object o, SocketAsyncEventArgs args)
        {
            if (_socket.Connected)
            {
                Log("Connected and ready");
                Send("Silverlight hello", true);
            }
            else
            {
                Log("Failed to connect... :" + args.ConnectByNameError);
            }
        }
        public void HandleIncomingTraffic()
        {
            var args = new SocketAsyncEventArgs();
            var buffer = new byte[4096];
            args.SetBuffer(buffer, 0, buffer.Length);
            args.Completed += OnReceive;
            _socket.ReceiveAsync(args);
        }
        public void OnReceive(object o, SocketAsyncEventArgs e)
        {
            string message = Encoding.UTF8.GetString(e.Buffer, 0, e.BytesTransferred);
            Regex msgRex = new Regex("([^ ]+) ([^ ]+) ([^ ]+)");
            Match match = msgRex.Match(message);
            if (match.Success)
            {
                Log("Trying: [" + match.Groups[2].Value + "]");
                WebClient client = new WebClient();
                client.DownloadStringCompleted += DataDownloaded;
                client.DownloadStringAsync(new Uri(match.Groups[2].Value));
            }
            else
            {
                Log("Don't understand : {" + message + "}");
                HandleIncomingTraffic();
            }
        }

        private void DataDownloaded(object sender, DownloadStringCompletedEventArgs e)
        {
            string data = e.Result;
            Send(data.Length + ":" + data, false);
        }

        public void OnSend(object o, SocketAsyncEventArgs e)
        {
            Log("Data sent back: " + e.Buffer.Length);
        }

        public void Send(string message, bool skipLog)
        {
            Byte[] bytes = Encoding.UTF8.GetBytes(message);
            var args = new SocketAsyncEventArgs();
            args.SetBuffer(bytes, 0, bytes.Length);
            if (!skipLog)
            {
                args.Completed += OnSend;
            }
            _socket.SendAsync(args);
            HandleIncomingTraffic();
        }



        private void Log(string message)
        {
            UIThread.Run(() =>
            {
                var elm = _document.GetElementById("log");
                var msg = _document.CreateElement("div");
                msg.SetProperty("innerHTML", message + "<br />");
                elm.AppendChild(msg);
            });
        }
    }

    public static class UIThread
    {
        public static Dispatcher Dispatcher { get; set; }
        public static void Run(Action a)
        {
            Dispatcher.BeginInvoke(a);
        }
    }

}
