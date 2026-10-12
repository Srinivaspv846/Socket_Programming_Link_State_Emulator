#include <bits/stdc++.h>
#include <sys/socket.h> // socket(), bind()
#include <netinet/in.h> // sockaddr_in, AF_INET
#include <arpa/inet.h>  // inet_addr(), htons()
#include <unistd.h>     // close()
#include <cstring>      // memset()
using namespace std;

#define MYPORT 7001
#define TOPORT 7000
#define BACKLOG 10
int main(){

    int sockfd,new_fd;
    sockfd = socket(AF_INET, SOCK_STREAM, 0);

    socklen_t sin_size;
    struct sockaddr_in my_addr,their_addr;
    my_addr.sin_family = AF_INET;
    my_addr.sin_port = htons(MYPORT);
    my_addr.sin_addr.s_addr = inet_addr("10.51.18.56");
    memset(&(my_addr.sin_zero), '\0', 8);
    bind(sockfd, (struct sockaddr *)&my_addr, sizeof(struct sockaddr));

    listen(sockfd,BACKLOG);

    sin_size = sizeof(struct sockaddr_in);
    new_fd = accept(sockfd, (struct sockaddr *)&their_addr, &sin_size);


    string msg="Hello Client";
    int flags=0;
    char buf[100];
    int len=100;
    int bytes_recieved;
    bytes_recieved=recv(new_fd,buf,len,0);
    cout<<bytes_recieved<<endl;
    cout<<buf<<endl;
    close(sockfd);
}