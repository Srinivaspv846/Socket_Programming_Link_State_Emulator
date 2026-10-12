#include <bits/stdc++.h>
#include <sys/socket.h> // socket(), bind()
#include <netinet/in.h> // sockaddr_in, AF_INET
#include <arpa/inet.h>  // inet_addr(), htons()
#include <unistd.h>     // close()
#include <cstring>      // memset()
using namespace std;

#define MYPORT 7000
#define TO_IP  "10.51.18.56"
#define TOPORT 7001

int main(){

    int sockfd;
    struct sockaddr_in to_addr;
    sockfd = socket(PF_INET, SOCK_STREAM, 0);
    to_addr.sin_family = AF_INET;
    to_addr.sin_port = htons(TOPORT);
    to_addr.sin_addr.s_addr = inet_addr(TO_IP);
    memset(&(to_addr.sin_zero), '\0', 8); 

    connect(sockfd, (struct sockaddr *)&to_addr, sizeof(struct sockaddr));


    char* msg="Hello Server";
    int len,bytes_sent;
    len=strlen(msg);
    bytes_sent=send(sockfd,msg,len,0);
    close(sockfd);
}