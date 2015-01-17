package org.xiphis.utils.common;

import org.junit.Test;

import static org.junit.Assert.*;

public class ImmutablePairTest {

  @Test
  public void testMake() throws Exception {
    ImmutablePair<Integer, Long> test = ImmutablePair.make(1, 2L);
    assertSame(1, test.first);
    assertSame(2L, test.second);
  }

  @Test
  public void testMake1() throws Exception {
    ImmutablePair<Integer, Long> test = ImmutablePair.make(Pair.make(1, 2L));
    assertSame(1, test.first);
    assertSame(2L, test.second);

  }
}