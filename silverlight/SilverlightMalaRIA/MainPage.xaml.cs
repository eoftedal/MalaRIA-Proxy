using System;
using System.Collections.Generic;
using System.IO;
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
        //Config
        private const int Port = 4502;
        private const string Hostname = "localhost";


        private readonly Socket _socket;
        private readonly HtmlDocument _document;



        public MainPage()
        {
            InitializeComponent();
            UIThread.Dispatcher = Dispatcher;
            _document = HtmlPage.Document;
            try
            {
                Log("Connecting back to malaria server (" + Hostname + ":" + Port + ")...");
                _socket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
                var args = new SocketAsyncEventArgs { RemoteEndPoint = new DnsEndPoint(Hostname, Port, AddressFamily.InterNetwork) };
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
                Send("Silverlight hello", null, true);
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
            var msgRex = new Regex("([^ ]+) ([^ ]+) ([^ ]+)( (.*))?");
            Match match = msgRex.Match(message);
            if (match.Success)
            {
                try
                {
                    Log("Trying: [" + match.Groups[2].Value + "]");
                    var client = new WebClient();
                    if (match.Groups[1].Value == "GET")
                    {
                        client.OpenReadCompleted += OnGetCompleted;
                        client.OpenReadAsync(new Uri(match.Groups[2].Value));
                    } else
                    {
                        client.UploadStringCompleted += OnPostCompleted;
                        client.UploadStringAsync(new Uri(match.Groups[2].Value), "POST", match.Groups[5].Value);
                    }
                }
                catch(Exception ex)
                {
                    Log("Error sending data: " + ex.Message);
                }
            }
            else
            {
                Log("Don't understand : {" + message + "}");
                HandleIncomingTraffic();
            }
        }


        private void OnPostCompleted(object sender, UploadStringCompletedEventArgs e)
        {
            if(e.Error != null)
            {
                Send("HTTP/1.1 502 Not accessible", false);
            } 
            else
            {
                byte[] data = Encoding.UTF8.GetBytes(e.Result);
                Send(data.Length + ":", data, false);
            }
        }

        private void OnGetCompleted(object sender, OpenReadCompletedEventArgs e)
        {
            if (e.Error != null)
            {
                Send("HTTP/1.1 502 Not accessible", false);
            }
            else
            {
                ReadFromStreamAndSendBack(e.Result);
            }
        }

        private void ReadFromStreamAndSendBack(Stream streamResponse)
        {
            var fullBuffer = new List<byte>();
            var buffer = new byte[4096];
            do
            {
                int length = streamResponse.Read(buffer, 0, buffer.Length);
                for (int i = 0; i < length; i++) fullBuffer.Add(buffer[i]);
            } while (fullBuffer.Count < streamResponse.Length);

            byte[] data = fullBuffer.ToArray();
            Send(data.Length + ":", data, false);
        }

        public class Context
        {
            public WebClient Client;
            public string Data;
        }

        

        public void OnSend(object o, SocketAsyncEventArgs e)
        {
            Log("Data sent back: " + e.Buffer.Length);
        }

        public void Send(string message, bool skipLog)
        {
            Send(message, null, skipLog);
        }

        public void Send(string message, byte[] data, bool skipLog)
        {
            Byte[] msgBytes = Encoding.UTF8.GetBytes(message);
            var args = new SocketAsyncEventArgs();
            var fullBuffer = new List<byte>();
            fullBuffer.AddRange(msgBytes);
            if (data != null)
            {
                fullBuffer.AddRange(data);
            }
            byte[] bytes = fullBuffer.ToArray();
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
