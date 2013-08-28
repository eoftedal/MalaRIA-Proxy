var net = require('net');
var http = require('http');
var Emitter = require('events').EventEmitter;
var urlParser = require('url');
var fs = require('fs');


var hostname = process.argv[2] || 'localhost';
var port = process.argv[3] || 8000;
var proxyPort = process.argv[4] || 8080;
var events = new Emitter();
events.setMaxListeners(50);

console.log("Starting node proxy for " + hostname + ":" + port);

var clients = [];
var clientId = 0;
var activeClient = -1;
var reqId = 0;

function isSet(val) {
	return typeof variable !== 'undefined';
}
function log(msg, rid) {
	console.log(Date.now(), isSet(rid) ? rid : "" , msg);
}


var proxyServer = http.createServer(function (req, res) {
	var url = urlParser.parse(req.url, true);
	if (url.hostname === 'malaria' || activeClient === -1) {
		if (url.pathname === '/setActive') {
			activeClient = parseInt(url.query.id, 10);
			log("Active client: " + activeClient);
			res.setHeader('Cache-Control', 'no-cache, no-store');
			res.writeHead(204);
			res.end();
			return;
		}
		if (url.pathname === '/jquery.min.js') {
			res.writeHead(200);
			fs.createReadStream('jquery.min.js').pipe(res);
			return;
		}
		res.write('<script src="jquery.min.js"></script>');
		res.write('<h1>Clients</h1>');
		for (var i in clients) {
			if (clients[i].status != 'closed') {
				res.write('<a href="#"" onclick="$.get(\'/setActive?id=' + clients[i].id + '\')">Client ' + clients[i].id + '</a>');
			}
		}
		res.end();
		return;
	}
	events.emit('client-' + activeClient, req, res);
}).listen(proxyPort);

var backchannel = net.createServer();
backchannel.listen(port);
backchannel.on('connection', function(socket) {
	var id = clientId++;
	log("Socket connected: " + id);
	var client = { id: id, socket: socket }
	clients.push(client);
	socket.once('data', function(chunk) {
		log("<- " + chunk.toString());
	});
	var requests = [];

	var handleRequest = function() {
		var request = requests.pop();
		var req = request.req;
		var res = request.res;
		log("Taking from queue (" + requests.length + ") :" + req.url);
		var state = { length: 0, rid: reqId++ };
		request.events.once('ready', function(data) {
			socket.write(req.method + " " + req.url + " " + req.headers["accept"]);
			if (data) {
				socket.write(" ");
				for (var i in data) {
					socket.write(data[i]);
					log(data[i].toString());
				}
			}
			socket.write('\n');
			log("-> " + req.method + " " + req.url + " " + req.headers["accept"] + "\n", state.rid);
			socket.once('data', function(chunk) {
				var first = chunk.toString().split(":")[0];
				log("<- " + first, state.rid, chunk.length);
				if (first.indexOf("HTTP/1.1 502 Not accessible") == 0) {
					request.events.emit('failed');
					return;
				}
				var length = parseInt(first);
				log("<- Data length: " + length, state.rid);
				state.contentLength = length;
				res.setHeader("Content-Length", length);
				res.writeHead(200);
				state.length = chunk.length - (first + ":").length;
				res.write(chunk.slice(chunk.length - state.length));
				if (state.length >= state.contentLength) {
					request.events.emit('done');
					return;
				}
				socket.on('data', function(chunk) {
					state.length += chunk.length;
					log("<- recevied " + chunk.length + " " + state.length + "/" + state.contentLength, state.rid);
					res.write(chunk);
					if (state.length >= state.contentLength) request.events.emit('done');
				});
			});
		});

		if (req.headers['content-length']) {
			var data = [];
			var dataLength = 0;
			req.on('data', function(chunk) {
				data.push(chunk);
				dataLength += chunk.length;
				if (dataLength >= parseInt(req.headers['content-length'], 10)) {
					request.events.emit('ready', data);
				}
			});
		} else {
			request.events.emit('ready');
		}

		request.events.once('failed', function() {
				res.writeHead(502);
				request.events.emit('done');			
		});
		request.events.once('done', function() {
			res.end();
			log("<- end " + requests.length);
			socket.removeAllListeners('data');
			if (requests.length > 0) {
				handleRequest();
			} else {
				events.once('client-' + id + '-new', handleRequest);
			}
		});
	}

	var doRequest = function(req, res) {
		requests.push({req: req, res: res, events: new Emitter()});
		log("Adding to queue (" + requests.length + ") :" + req.url);
		events.emit("client-" + id + "-new");
	}

	events.on('client-' + id, doRequest);
	events.once('client-' + id + '-new', handleRequest);


	socket.on('close', function(socket) {
		if (activeClient === id) activeClient = -1;
		log("Socket closed: " + id);
		events.removeAllListeners('client-' + id);
		events.removeAllListeners('client-' + id + '-new');
		client.status = 'closed';
	});

});








var flexPolicyServer = net.createServer();
flexPolicyServer.listen(843);
flexPolicyServer.on('connection', function(socket) {
	log("Serving flex port policy");
	socket.setEncoding('utf8');
	socket.write('<?xml version="1.0"?>\n');
	socket.write('<!DOCTYPE cross-domain-policy SYSTEM "/xml/dtds/cross-domain-policy.dtd">');
	socket.write('<cross-domain-policy>');
	socket.write('<site-control permitted-cross-domain-policies="master-only"/>');
	socket.write('<allow-access-from domain="' + hostname + '" to-ports="' + port + '" />');
	socket.write('</cross-domain-policy>');
	socket.end();
});
var silverlightPolicyServer = net.createServer();
silverlightPolicyServer.listen(943);
silverlightPolicyServer.on('connection', function(socket) {
	log("Serving silverlight port policy");
	socket.setEncoding('utf8');	
	socket.write('<?xml version="1.0" encoding="utf-8"?>');
	socket.write('<access-policy>');
	socket.write('  <cross-domain-access>');
	socket.write('    <policy>');
	socket.write('      <allow-from>');
	socket.write('        <domain uri="' + hostname + '" />');
	socket.write('        <domain uri="http://' + hostname + '" />');
	socket.write('      </allow-from>');
	socket.write('      <grant-to>');
	socket.write('        <socket-resource port="' + port + '" protocol="tcp" />');
	socket.write('      </grant-to>'); 
	socket.write('    </policy>');
	socket.write('  </cross-domain-access>');
	socket.write('</access-policy>');
	socket.end();
});






