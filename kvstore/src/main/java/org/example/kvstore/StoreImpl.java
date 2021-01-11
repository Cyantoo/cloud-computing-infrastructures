package org.example.kvstore;

import org.example.kvstore.cmd.Command;
import org.example.kvstore.cmd.CommandFactory;
import org.example.kvstore.cmd.Get;
import org.example.kvstore.cmd.Put;
import org.example.kvstore.cmd.Reply;
import org.example.kvstore.distribution.Strategy;
import org.example.kvstore.distribution.ConsistentHash;

import org.jgroups.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoreImpl<K,V> extends ReceiverAdapter implements Store<K,V> {

    private String name;
    private Strategy strategy;
    private Map<K,V> data;
    private CommandFactory<K,V> factory;
    private JChannel channel;
    private ExecutorService workers;
    private CompletableFuture<V> pending;


    private class CmdHandler implements Callable<Void>
    {
      Address caller;
      Command<K,V> commandToExecute;
      public CmdHandler(Address caller, Command<K,V> commandToExecute)
      {
        this.caller = caller;
        this.commandToExecute = commandToExecute;
      }

      public Void call()
      {
        K key = commandToExecute.getKey();
        V value = commandToExecute.getValue();
        if(commandToExecute instanceof Reply)
        {
          pending.complete(value);
        }
        else{
          if(commandToExecute instanceof Put)
          {
            value = data.put(key, value);
          }
          else if(commandToExecute instanceof Get)
          {
            value = data.get(key);
          }
          Reply<K,V> reply = factory.newReplyCmd(key, value);
          send(caller, reply);
        }
        return null;
      }
    }

    public StoreImpl(String name) {
        this.name = name;
        data = new HashMap<K,V>();
    }

    public void init() throws Exception{ // may be stuff to do here
      channel = new JChannel();
      channel.setReceiver(this);
      channel.connect("KeyValueStore");
      workers = Executors.newCachedThreadPool();
    }

    @Override
    public void viewAccepted(View new_view)
    {
      strategy = new ConsistentHash(new_view);
    }

    // On ne doit pas créer de pending si elle n'est pas complétée avant
    @Override
    public V get(K k) {
      Address addr = strategy.lookup(k);
      if(addr == channel.getAddress())
      {
        return data.get(k);
      }
      else{
        pending = new CompletableFuture<V>();
        send(addr, factory.newGetCmd(k));
        try
          {
            return(pending.get());
          }
          catch(Exception e){
            e.printStackTrace();
            return null;
          }
      }
    }

    @Override
    public V put(K k, V v) {
      Address addr = strategy.lookup(k);
      if(addr == channel.getAddress())
        return data.put(k, v);
      else{
          pending = new CompletableFuture<V>();
          send(addr, factory.newPutCmd(k, v));
          try
          {
            return(pending.get());
          }
          catch(Exception e){
            e.printStackTrace();
            return null;
          }
        }
      }

    @Override
    public String toString(){
        return "Store#"+name+"{"+data.toString()+"}";
    }

    public void send(Address dst, Command<K,V> cmd)
    {
      Message msg = new Message(dst, null, cmd);
      try{
        channel.send(msg);
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }

    @Override
    public void receive(Message msg)
    {
      Command<K,V> cmd = (Command<K,V>) msg.getObject();
      workers.submit(new CmdHandler(msg.getSrc(), cmd));
    }

}
