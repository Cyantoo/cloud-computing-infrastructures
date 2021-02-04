package org.example.kvstore.distribution;

import org.jgroups.Address;
import org.jgroups.View;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.TreeSet;

import java.io.Serializable;


public class ConsistentHash implements Serializable,Strategy{

    private TreeSet<Integer> ring;
    private Map<Integer,Address> addresses;

    public ConsistentHash(List<Address> members){
      ring = new TreeSet<Integer>();
      addresses = new HashMap<Integer, Address>();
      for(Address member:members) //build the ring
      {
        ring.add(member.hashCode());
        addresses.put(member.hashCode(), member);
      }
    }

    @Override
    public Address lookup(Object key){
      Integer hash = key.hashCode();
      if (ring.higher(hash) != null)
        return addresses.get(ring.higher(hash));
      else
      {
        return addresses.get(ring.first()); // cycle around the ring

      }
    }

}
