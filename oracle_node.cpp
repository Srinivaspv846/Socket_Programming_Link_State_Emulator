#include <bits/stdc++.h>
using namespace std;

int main(){

    ifstream fin("config.txt");
    string line;
    vector<vector<int>> adj;    
    while(getline(fin,line)){

        if(line.empty()) continue;
        if(line[0]=='#') continue;

        istringstream lin(line);
        adj.push_back({});
        int n=adj.size();
        int x;
        while(lin>>x){
            adj[n-1].push_back(x);
        }
    }
    // for(auto x:adj){
    //     for(int y:x) cout<<y<<" ";
    //     cout<<endl;
    // }
    int n=adj.size()+1;
    
}