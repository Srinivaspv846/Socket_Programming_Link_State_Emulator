#include <bits/stdc++.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <sys/stat.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <cstdint>
using namespace std;

struct VNClient{
    int fd;
    char id;
    int index;
    bool connected_msg_rcvd;
    uint8_t recv_buf[6];
    int recv_len;
    uint8_t ip_bytes[4];    //network byte order
    uint16_t udp_port;      //host byte order
};

void set_nonblocking(int fd){
    int flags=fcntl(fd,F_GETFL,0);
    if(flags!=-1){
        fcntl(fd,F_SETFL,flags | O_NONBLOCK);
    }
}

bool extract(const string& filename,vector<vector<int>>& adj,int& num_nodes){

    ifstream fin(filename);
    if(!fin.is_open()){cerr<<"Error : Can't open file: "<<filename<<endl;return false;}

    vector<vector<int>> upper_adj;
    string line;
    while(getline(fin,line)){
        size_t first=line.find_first_not_of(" \t\r\n");
        if(first==string::npos || line[first]=='#') continue;
        istringstream l(line.substr(first));
        vector<int> row;int x;
        while(l>>x) row.push_back(x);
        if(!row.empty()) upper_adj.push_back(row);
    }
    int n=upper_adj.size()+1;
    if(n<=1 || n>=26){cerr<<"ERROR: Invalid no of nodes ("<<n<<")."<<endl;return false;}
    for(int i=0;i<n-1;i++){
        if((int)upper_adj[i].size()!=(n-1-i)){
            cerr<<"Error: Row "<<i<<" expected "<<(n-1-i)<<" entries, got "<<upper_adj[i].size()<<endl;
            return false;
        }
    }

    num_nodes=n;
    adj.assign(n,vector<int>(n,-1));
    for(int i=0;i<n;i++){
        adj[i][i]=0;
        for(int j=i+1;j<n;j++){
            int cost=upper_adj[i][j-(i+1)];
            adj[i][j]=cost;adj[j][i]=cost;
        }
    }
    return true;
}


void broadcast_link_state(vector<VNClient>& clients,const vector<vector<int>>& adj,int n){

    int ready_count=0;
    for(const auto& c : clients){
        if(c.fd!=-1 && c.index<n && c.connected_msg_rcvd) ready_count++;
    }
    
    if(ready_count<n) return;
    cout<<"[ON] Broadcasting LINK-STATE to connected virtual nodes..."<<endl;

    for(auto& target : clients){

        if(target.fd==-1 || !target.connected_msg_rcvd) continue;

        vector<uint8_t> packet;
        uint8_t num_tuples=0;
        packet.push_back(0);
        packet.push_back((uint8_t)target.id);
        for(int b=0;b<4;b++) packet.push_back(target.ip_bytes[b]);
        uint16_t net_self_port=htons(target.udp_port);
        uint8_t* p_self_port=reinterpret_cast<uint8_t*>(&net_self_port);
        packet.push_back(p_self_port[0]);
        packet.push_back(p_self_port[1]);
        uint16_t net_self_cost=htons(0);
        uint8_t* p_self_cost=reinterpret_cast<uint8_t*>(&net_self_cost);
        packet.push_back(p_self_cost[0]);
        packet.push_back(p_self_cost[1]);
        num_tuples++;
        
        if(target.index<n){
            int u=target.index;
            for(int v=0;v<n;v++) {
                if(u!=v&&adj[u][v]>0){
                    // Find client v
                    for(const auto& nb:clients){
                        if(nb.index==v&&nb.connected_msg_rcvd){
                            packet.push_back((uint8_t)nb.id);
                            for(int b=0;b<4;b++) packet.push_back(nb.ip_bytes[b]);

                            uint16_t net_port=htons(nb.udp_port);
                            uint8_t* p_port=reinterpret_cast<uint8_t*>(&net_port);
                            packet.push_back(p_port[0]);
                            packet.push_back(p_port[1]);

                            int16_t net_cost=htons((int16_t)adj[u][v]);
                            uint8_t* p_cost=reinterpret_cast<uint8_t*>(&net_cost);
                            packet.push_back(p_cost[0]);
                            packet.push_back(p_cost[1]);
                            num_tuples++;
                            break;
                        }
                    }
                }
            }
        }

        packet[0]=num_tuples;
        size_t total_sent=0;
        while (total_sent<packet.size()) {
            ssize_t sent=send(target.fd,packet.data()+total_sent, packet.size()-total_sent,0);
            if (sent<0){
                if(errno==EAGAIN || errno==EWOULDBLOCK) continue;
                cerr<<"[ON] Failed to send LINK-STATE to node "<<target.id<<endl;
                break;
            }
            total_sent+=sent;
        }       
    }
}


int main(int argc,char* argv[]){

    string config_file=(argc>=2)? argv[1]:"config.txt";
    vector<vector<int>> adj;
    int n=0;
    if(!extract(config_file,adj,n)) return 1;
    cout<<"[ON] Loaded config "<<config_file<<" with n = "<<n<<" nodes."<<endl;
    for(auto& x:adj){
        for(int y:x) cout<<y<<" ";
        cout<<endl;
    }

    struct stat file_stat;
    time_t last_mtime=0;
    if(stat(config_file.c_str(),&file_stat)==0) last_mtime=file_stat.st_mtime;

    int listen_fd=socket(AF_INET,SOCK_STREAM,0);
    if(listen_fd<0){perror("socket");return 1;}

    int opt=1;
    setsockopt(listen_fd,SOL_SOCKET,SO_REUSEADDR,&opt,sizeof(opt));
    set_nonblocking(listen_fd);

    struct sockaddr_in serv_addr;
    memset(&serv_addr,0,sizeof(serv_addr));
    serv_addr.sin_family=AF_INET;
    serv_addr.sin_addr.s_addr=htonl(INADDR_ANY);
    serv_addr.sin_port=htons(5000);

    if(bind(listen_fd,(struct sockaddr*)&serv_addr,sizeof(serv_addr))<0){
        perror("bind failed on TCP port 5000");
        close(listen_fd);return 1;
    }

    if(listen(listen_fd,30)<0){
        perror("listen");
        close(listen_fd);return 1;
    }
    cout<<"[ON] Listening for Virtual Nodes on TCP port 5000"<<endl;

    vector<VNClient> clients;
    time_t last_config_check=time(nullptr);

    while(true){

        fd_set read_fds;
        FD_ZERO(&read_fds);
        FD_SET(listen_fd,&read_fds);
        int max_fd=listen_fd;

        for(const auto& c:clients){
            if(c.fd!=-1){
                FD_SET(c.fd,&read_fds);
                if(c.fd>max_fd) max_fd=c.fd;
            }
        }

        struct timeval tv;
        tv.tv_sec=0;tv.tv_usec=500000;

        int activity=select(max_fd+1,&read_fds,nullptr,nullptr,&tv);
        if(activity<0 && errno!=EINTR){perror("select");break;}

        if(FD_ISSET(listen_fd,&read_fds)){
            struct sockaddr_in cli_addr;
            socklen_t cli_len=sizeof(cli_addr);
            int conn_fd=accept(listen_fd,(struct sockaddr*)&cli_addr,&cli_len);
            if(conn_fd>=0){
                set_nonblocking(conn_fd);
                int idx=clients.size();
                if(idx<26){
                    VNClient vc;
                    vc.fd=conn_fd;
                    vc.id=(char)('A'+idx);
                    vc.index=idx;
                    vc.connected_msg_rcvd=false;
                    vc.recv_len=0;
                    vc.udp_port=0;
                    clients.push_back(vc);
                    cout<<"[ON] Accepted TCP connection #"<<(idx + 1)<<" -> Assigned Node '"<<vc.id<<"'"<<(idx>=n?" (Disconnected Island)" : "")<<endl;
                }
                else{
                    cerr<<"[ON] Maximum of 26 nodes reached. Rejecting connection."<<endl;
                    close(conn_fd);                    
                }
            }
        }

        bool new_connect_completed=false;
        for(auto&c : clients){
            if(c.fd!=-1 && FD_ISSET(c.fd,&read_fds)){
                if(!c.connected_msg_rcvd){
                    ssize_t n=recv(c.fd,c.recv_buf+c.recv_len,6-c.recv_len,0);
                    if(n==0 || (n<0 && errno!=EAGAIN && errno!=EWOULDBLOCK)){
                        cout<<"[ON] Node "<<c.id<<" disconnected."<<endl;
                        close(c.fd);c.fd=-1;
                    }
                    else if(n>0){
                        c.recv_len+=n;
                        if(c.recv_len==6){
                            memcpy(c.ip_bytes,c.recv_buf,4);
                            uint16_t net_port;
                            memcpy(&net_port,c.recv_buf+4,2);
                            c.udp_port=ntohs(net_port);
                            c.connected_msg_rcvd=true;
                            new_connect_completed=true;
                            char ip_str[INET_ADDRSTRLEN];
                            inet_ntop(AF_INET,c.ip_bytes,ip_str, sizeof(ip_str));
                            cout<<"[ON] Received CONNECT from Node "<<c.id<<" -> ("<<ip_str<<", UDP Port "<<c.udp_port<<")"<<endl;
                        }
                    }
                }
                else{
                    uint8_t dummy[256];
                    ssize_t n=recv(c.fd,dummy,sizeof(dummy),0);
                    if(n==0 || (n<0&&errno!=EAGAIN&&errno!=EWOULDBLOCK)) {
                        cout<<"[ON] Node "<<c.id<<" closed connection."<<endl;
                        close(c.fd);
                        c.fd=-1;
                    }
                }
            }
        }

        if(new_connect_completed){
            broadcast_link_state(clients,adj,n);
        }

        time_t now=time(nullptr);
        if(now-last_config_check>=1){
            last_config_check=now;
            if(stat(config_file.c_str(),&file_stat)==0){
                if(file_stat.st_mtime!=last_mtime&&file_stat.st_size>0){
                    vector<vector<int>> updated_adj;
                    int updated_N=0;
                    if(extract(config_file,updated_adj,updated_N)){
                        last_mtime=file_stat.st_mtime;
                        if(updated_adj!=adj){
                            cout<<"[ON] Detected topology change in '"<<config_file<<"'."<<endl;
                            adj=updated_adj;
                            broadcast_link_state(clients,adj,n);
                        }
                    }
                }       
            }
        }
    }
    for(auto& c:clients){
        if(c.fd!=-1) close(c.fd);
    }
    close(listen_fd);
    return 0;
}