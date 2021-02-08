# A key-value store (almost) from scratch

By Mikaël Boichot, Antoine Colin, Léa Ildefonse, Chloé Jabea

The final code is available online at 
https://github.com/Cyantoo/cloud-computing-infrastructures/tree/kvstore/kvstore. 
You are welcome to look at previous commits if needed. We later indicate some points when this is particularly interesting.

## 1. The JGroups library

**[Task]** We followed the online tutorial to discover the basics of jgroups.

## 2. Provided code
We went through the provided code and explanations to understand what we would be working with.

## 3. Implementing the key-value store

### 3.1 Local operations on the data

**[Task]** First, we complete the ``ConsistentHash`` class in ``ConsistentHash.java`` with both a constructor and a ``lookup`` function, according to the ConsistentHash paradigm we studied in class, with a ring.

**[Task]** Then, we make it so ``StoreImpl`` extends ``ReceiverAdapter``. This means adding a ``viewAccepted`` method, and using the constructor created at the precedent question to assign a ConsistentHash strategy corresponding to the new view.

**[Task]** In the case where the local node is in charge of storing the key, ``put`` and ``get`` methods are really simple, since they only act on the local field ``data``. 

### 3.2 Handling remote data

The first four tasks are pretty straightforward. See the code for details.

#### Management of remote calls
**[Task]** To complete the management of remote calls, we did the following :
- Create a ``pending`` field
- Modifiy the ``put`` and ``get`` functions to create a future in ``pending``, thus locking it, then sending a command to the right address (found thanks to ``lookup``) and waiting for the result in ``pending``.
- In CmdHandler, if the Command is a ``Reply``, we complete the future to release the lock.

The state after this task is available at https://github.com/Cyantoo/cloud-computing-infrastructures/tree/f60a05f47aa00aa3e02cf8189de011b31b44312b.

**[Task]** Here, we simply merge the contents of the ``put`` and ``get`` method in one method ``execute`` which is ``synchronized``.
The state after this task is available at https://github.com/Cyantoo/cloud-computing-infrastructures/tree/f88579e2ab8a4d82ea6949e864922c8ee5684e2c.

**[Task]** ``CmdHandler`` used to directly modify or fetch data from the store. This was not synchronized and could therefore lead to problems if two instances of CmdHandler or the method ``execute`` accessed the same data at the same time. In order to avoid this, we simply make the execution in CmdHandler as a call to ``execute``, which is synchronized, hence avoiding problems with concurrent access.

The state after this task is available at https://github.com/Cyantoo/cloud-computing-infrastructures/tree/20c3406daaaba4a421aadacd2356392443fa28c6.

## 4. Data migration

From this point on, we modified significantly the code that was provided. This is why we provide links to commits before these modifications.

**[Task]** We propose and implement a mechanism for data migration. 

Data migration means migrating the data from an old set of nodes to a new set of nodes, such that it respects the rules defined in the strategy, whenever a node joins or leaves. These two cases require different steps. 
#### 1. When a node joins
This is mostly taken care of in the ``viewAccepted`` method. Theoretically, it should be pretty simple : when a node joins, ``viewAccepted`` is called in all the existing nodes. In each of these nodes, we can then compute the new strategy. Then each node transfers their part of the data so that it follows the new strategy. 
In practice, when ``viewAccepted`` is called, the new node has often not yet actually joined the cluster and is still sending ``JOIN`` requests to the coordinator of the group. if the coordinator is busy migrating data, it can't answer the ``JOIN`` request, thus blocking the new node from entering, and the data to be migrated to it. In order to avoid that, we delay the execution of the data migration by one second, with ``ScheduledExecutorService.schedule``. This is enough time for the coordinator to answer the JOIN request and for the new node to actually join.

####  2. When a node leaves
This part is a bit harder than the former. First, there is no implemented way to force a node to leave, so we implement it with the method ``end()``. 

 When a node leaves, we need to migrate the data from this node before it leaves the channel, otherwise communication will be cut and it won't be able to send its data to the other nodes. Interestingly, because of the ring structure, when a node A leaves, all its data goes to the same node B. We can find that node by first computing the new strategy without A, then using ``lookup(address of A)``. To send all the data from node A to node B, the method we found that was most respectful of the paradigm used until this was to :
1. send to B the new strategy. This required modifying the ``receive`` method to handle incoming strategies.
2. then migrate the data from A using normal ``put`` calls with the new strategy. The lookups will all return B's address. B also has the new strategy, and therefore puts the data in its own store.

**[Task]** We now look at testing our approach and create a test for it. We first start testing with the previous unit tests. We realize there are raised exceptions when a new store joins but the data has not been migrated yet. This is because those tests don't assume that the service is interrupted. We therefore add sleeps in the tests to give time to data migration to occur, and therefore for the service to be inactive before a node joins or leaves. 

By studying that, we also realize that nodes don't leave immediately after a function is finished executing. This causes problems with migration because a node may be there when it's not expected, and then leave suddenly with its data, since data migration doesn't happen when a node leaves automatically (which seems to happen when the garbage collector gets to that node). To ensure nodes leave when they are supposed to, we add calls to ``Store.end()`` in the tests. As for joins, we add sleeps to make sure data is properly migrated after a node leaves before any other action. 

Finally, we design a ``dataMigration`` test that tests migration both when a node joins and leaves. The test passes successfully.

The state after this task is available at https://github.com/Cyantoo/cloud-computing-infrastructures/tree/9c26251928f29447465114e6f59e74dde9c98c61.
