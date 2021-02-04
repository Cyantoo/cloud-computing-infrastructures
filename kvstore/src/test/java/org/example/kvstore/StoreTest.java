package org.example.kvstore;

import org.junit.Test;

import java.util.Random;

public class StoreTest {
    /*
    @Test
    public void baseOperations() {
        System.out.println("Single Store test");
        StoreManager manager = new StoreManager();
        Store<Integer, Integer> store = manager.newStore();

        assert store.get(1) == null;

        store.put(42, 1);
        assert store.get(42).equals(1);

        assert store.put(42, 2).equals(1);
        store.end();
    }
    
    @Test
    public void multipleStores(){
        System.out.println("Multiple Stores test");
        int NCALLS = 1000;
        Random rand = new Random(System.nanoTime());

        StoreManager manager = new StoreManager();
        Store<Integer, Integer> store1 = manager.newStore();
        Store<Integer, Integer> store2 = manager.newStore();
        Store<Integer, Integer> store3 = manager.newStore();

        for (int i=0; i<NCALLS; i++) {
            int k = rand.nextInt();
            int v = rand.nextInt();
            store1.put(k, v);
            assert rand.nextBoolean() ? store2.get(k).equals(v) : store3.get(k).equals(v);
        }
        store1.end();
        store2.end();
        store3.end();

    }
    */
    
    @Test
    public void dataMigration(){
        System.out.println("Data migration test");
        Random rand = new Random(System.nanoTime());
        int N = 10;
        int[] testValues = new int[N];
        int[] testKeys = new int[N];
        for (int i = 0; i < N; i++) {
            testValues[i] = rand.nextInt();
            testKeys[i] = rand.nextInt();
        }
        StoreManager manager = new StoreManager();
        Store<Integer, Integer> store1 = manager.newStore();
        for (int i = 0; i < N/2; i++) {
            store1.put(testKeys[i], testValues[i]);
        }

        Store<Integer, Integer> store2 = manager.newStore();
        Store<Integer, Integer> store3 = manager.newStore();

        try{
            Thread.sleep(1000); // needed to give time to finish migration
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        for (int i = 0; i < N/2; i++) {
            assert rand.nextBoolean() ? store2.get(testKeys[i]).equals(testValues[i]) : store3.get(testKeys[i]).equals(testValues[i]);
        }

        for (int i = N/2; i < N; i++) {
            store3.put(testKeys[i], testValues[i]);
        }
        //store3.end();

        for (int i = 0; i < N; i++) {
            assert rand.nextBoolean() ? store2.get(testKeys[i]).equals(testValues[i]) : store1.get(testKeys[i]).equals(testValues[i]);
        }

        store1.end();
        store2.end();
        store3.end();

    }
    
    
    

}
