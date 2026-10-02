package com.masternova.java.generics;

/** Something that can be looked up by a key — e.g. a payment gateway by its provider. */
public interface Keyed<K> {
  K key();
}
