package org.example.kvstore.distribution;

import org.jgroups.Address;
import org.jgroups.View;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.TreeSet;

public class ConsistentHash implements Strategy{

    private TreeSet<Integer> ring;
    private Map<Integer,Address> addresses;

    public ConsistentHash(View view){
      List<Address> members = view.getMembers();
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
