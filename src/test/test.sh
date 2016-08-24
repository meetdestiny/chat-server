#!/usr/bin/expect
spawn telnet localhost 8080
expect "Chat with me!"
sleep 1
send "JOIN abc\n"
sleep 1
send "SEND abc some message\n"
sleep 1
send "SEND abc some message 2\n"
sleep 1
send "SEND abc some message 3\n"
sleep 1
send "SEND abc some message 4\n"
sleep 1
send "SEND abc some message 5\n"
sleep 1 
send "QUIT\n"
sleep 1 
