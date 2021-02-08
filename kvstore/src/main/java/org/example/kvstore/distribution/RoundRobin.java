package org.example.kvstore.distribution;

import org.jgroups.Address;
import org.jgroups.View;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.TreeSet;

import java.io.Serializable;


public class RoundRobin implements Serializable,Strategy{



    public RoundRobin(List<Address> members){
      
    }

    @Override
    public Address lookup(Object key){

    }

}
