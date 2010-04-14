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
            Regex msgRex = new Regex("([^ ]+) ([^ ]+) ([^ ]+)( (.*))?");
            Match match = msgRex.Match(message);
            if (match.Success)
            {
                try
                {
                    Log("Trying: [" + match.Groups[2].Value + "]");
                    var request = WebRequest.Create(new Uri(match.Groups[2].Value)) as HttpWebRequest;
                    request.Method = match.Groups[1].Value;
                    request.Accept = match.Groups[3].Value;
                    var context = new Context {Request = request};
                    if (request.Method == "POST")
                    {
                        context.Data = match.Groups[5].Value;
                    }
                    request.BeginGetRequestStream(GetRequestStreamCallback, context);
                }
                catch(Exception ex)
                {
                    Log("Fack 2");
                }
            }
            else
            {
                Log("Don't understand : {" + message + "}");
                HandleIncomingTraffic();
            }
        }
        public class Context
        {
            public HttpWebRequest Request;
            public string Data;
        }

        private void GetRequestStreamCallback(IAsyncResult asynchronousResult)
        {
            var context = (Context)asynchronousResult.AsyncState;
            var request = context.Request;
            Stream postStream = request.EndGetRequestStream(asynchronousResult);

            if (context.Data != null)
            {
                byte[] byteArray = Encoding.UTF8.GetBytes(context.Data);

                postStream.Write(byteArray, 0, byteArray.Length);
            }
            postStream.Close();
            request.BeginGetResponse(GetResponseCallback, request);
        }

        private void GetResponseCallback(IAsyncResult asynchronousResult)
        {
            var request = (HttpWebRequest)asynchronousResult.AsyncState;
            var response = (HttpWebResponse)request.EndGetResponse(asynchronousResult);
            Stream streamResponse = response.GetResponseStream();
            var fullBuffer = new List<byte>();
            var buffer = new byte[4096];
            int length;
            do
            {
                length = streamResponse.Read(buffer, 0, buffer.Length);
                for (int i = 0; i < length; i++) fullBuffer.Add(buffer[i]);
            } while (fullBuffer.Count < streamResponse.Length);

            byte[] data = fullBuffer.ToArray();
            Send(data.Length + ":", data, false);
        }

        public void OnSend(object o, SocketAsyncEventArgs e)
        {
            Log("Data sent back: " + e.Buffer.Length);
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
