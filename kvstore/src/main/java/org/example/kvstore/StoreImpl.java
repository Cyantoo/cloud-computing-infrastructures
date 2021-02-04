package org.example.kvstore;

import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StoreImpl<K,V> extends ReceiverAdapter implements Store<K,V> {

    private String name;
    private Strategy strategy;
    private Map<K,V> data;
    private CommandFactory<K,V> factory;
    private JChannel channel;
    private ExecutorService workers;
    private CompletableFuture<V> pending;

    public StoreImpl(String name) {
        this.name = name;
        data = new HashMap<K,V>();
    }

    public void init() throws Exception{ // may be stuff to do here
      workers = Executors.newCachedThreadPool();
      factory = new CommandFactory<K,V>();
      channel = new JChannel();
      channel.setReceiver(this);
      channel.connect("KeyValueStore");

    }

    public void end(){
      channel.close();
    }

    @Override
    public void viewAccepted(View new_view)
    {
      ScheduledExecutorService oneThreadScheduleExecutor = Executors.newScheduledThreadPool(1);

      strategy = new ConsistentHash(new_view);
      System.out.println("New view : "+new_view.getMembers().size());
      
      oneThreadScheduleExecutor.schedule(migrateDataRunnable, 1, TimeUnit.SECONDS);    
    }

    Runnable migrateDataRunnable = new Runnable()
    {
      public void run(){
        System.out.println("Starting migration at "+channel.getAddressAsString());
        Map<K,V> old_data = new HashMap<K,V>(data);
        data =  new HashMap<K,V>();
        old_data.forEach(
          (K k,V v) -> put(k, v)
          );
        System.out.println("Finished migration at "+channel.getAddressAsString());
      }
      
    };

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
            channel.close();
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
        channel.close();
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
        channel.close();
      }
    }

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

}
