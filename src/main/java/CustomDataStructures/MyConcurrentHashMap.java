package CustomDataStructures;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

// custom concurrent hashmap implementation
public class MyConcurrentHashMap<K,V> extends HashMap<K,V> {
    final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(); // read write lock for thread safety

    public MyConcurrentHashMap(){
        super(); // creates hashmap
    }

    @Override
    public V get(Object key){
        // acquire lock for read - waits otherwise
        readWriteLock.readLock().lock();
        try{
            // call hashmap
            return super.get(key);
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public V put(K key, V value){
        readWriteLock.writeLock().lock();
        try{
            // call hashmap method
            return super.put(key,value);
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public boolean containsKey(Object key){
        readWriteLock.readLock().lock();
        try{
            // call hashmap method
            return super.containsKey(key);
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public V putIfAbsent(K key, V value){
        readWriteLock.writeLock().lock();
        try {
            return super.putIfAbsent(key, value);
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        readWriteLock.writeLock().lock();
        try{
            return super.computeIfAbsent(key,mappingFunction);
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }


}
