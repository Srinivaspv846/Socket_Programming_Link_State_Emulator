CXX      = g++
CXXFLAGS = -Wall -O2 -std=c++11
ON_SRC   = oracle_node.cpp
CONFIG  ?= config.txt

# --- positional args for: make vn <N> <ON_IP> <ON_PORT> ---
ARGS    := $(filter-out vn on clean oracle_node vn.class,$(MAKECMDGOALS))
N       := $(or $(word 1,$(ARGS)),3)
ON_IP   := $(or $(word 2,$(ARGS)),$(shell hostname -I | awk '{print $$1}'))
ON_PORT := $(or $(word 3,$(ARGS)),5000)

# make the extra words harmless targets
$(foreach a,$(sort $(ARGS)),$(eval $(a): ; @:))

oracle_node: $(ON_SRC)
	$(CXX) $(CXXFLAGS) -o oracle_node $(ON_SRC)

vn.class: vn.java
	javac vn.java

# make on [config]   -> compile and run the ON
on: oracle_node
	./oracle_node $(or $(firstword $(ARGS)),$(CONFIG))

# make vn <N> <ON_IP> <ON_PORT>  -> N terminals, UDP ports 5001..5000+N
vn: vn.class
	@echo "Starting $(N) VNs -> ON $(ON_IP):$(ON_PORT)"
	@for i in $$(seq 1 $(N)); do \
	  port=$$((5000 + i)); \
	  cmd="java vn $(ON_IP) $(ON_PORT) $$port; exec bash"; \
	  if command -v gnome-terminal >/dev/null 2>&1; then \
	    gnome-terminal --title="VN $$port" -- bash -c "$$cmd"; \
	  elif command -v xterm >/dev/null 2>&1; then \
	    xterm -T "VN $$port" -e bash -c "$$cmd" & \
	  else \
	    echo "No gnome-terminal or xterm found"; exit 1; \
	  fi; \
	  sleep 1; \
	done

clean:
	rm -f oracle_node *.class

.PHONY: on vn clean