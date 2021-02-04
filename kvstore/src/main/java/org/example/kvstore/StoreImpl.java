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
          value = execute(commandToExecute);
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
      factory = new CommandFactory<K,V>();
    }

    @Override
    public void viewAccepted(View new_view)
    {
      strategy = new ConsistentHash(new_view);
    }

    synchronized V execute(Command<K,V> cmd){
      K k = cmd.getKey();
      Address addr = strategy.lookup(k);
      if(addr.equals(channel.getAddress()))
      {
        if(cmd instanceof Get)
          return data.get(k);
        else
          return data.put(k, cmd.getValue());
      }
      else{
        pending = new CompletableFuture<V>();
        send(addr, cmd);
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
    public V get(K k) {
      return(execute(factory.newGetCmd(k)));
    }

    @Override
    public V put(K k, V v) {
      return(execute(factory.newPutCmd(k, v)));
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
      try{
        workers.submit(new CmdHandler(msg.getSrc(), cmd));
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }

}
