How to compile
--------------
javac malaria\*.java

How to run
----------
java malaria.MalariaServer <hostname> <port> [http-proxy-port]
 - hostname 	   - hostname on which the flex or silverlight proxy is running
 - port		       - port which the flex or silverlight proxy will connect to
                     (Use 8081 for Flex, 4502 for Silverlight)
 - http-proxy-port - on which port should MalaRIA launch a HTTP proxy
                     this is the port you should set attacker's proxy to
                     Defaults to 8080.
