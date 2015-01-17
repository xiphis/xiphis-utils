package org.xiphis.utils.common;

import org.junit.Test;

import static org.junit.Assert.*;

public class PairTest {

  @Test
  public void testMake() throws Exception {
    Pair<Integer, Long> test = Pair.make(1, 2L);
    assertSame(1, test.first);
    assertSame(2L, test.second);
  }

  @Test
  public void testMake1() throws Exception {
    Pair<Integer, Long> test = Pair.make(ImmutablePair.make(1, 2L));
    assertSame(1, test.first);
    assertSame(2L, test.second);
  }

  @Test
  public void testMake2() throws Exception {
    Pair<Integer, Long> test0 = new Pair<>();
    test0.first = 1;
    test0.second = 2L;
    Pair<Integer, Long> test = Pair.make(test0);
    assertSame(1, test.first);
    assertSame(2L, test.second);
    assertNotSame(test0, test);
  }
}