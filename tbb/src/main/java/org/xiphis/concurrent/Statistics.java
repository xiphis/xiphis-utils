/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.xiphis.concurrent;

public class Statistics
{

  public final int[] execute_count = new int[1];
  public final int[] proxy_bypass_count = new int[1];
  public final int[] proxy_execute_count = new int[1];
  public final int[] proxy_steal_count = new int[1];
  public final int[] mail_received_count = new int[1];
  public final int[] reflect_construct_count = new int[1];
  public final int[] reflect_newinstance_count = new int[1];
  public final int[] steal_count = new int[1];
  public final long[] allocate_overhead = new long[1];
  public final long[] spawn_overhead = new long[1];
  public final long[] wait_for_all_overhead = new long[1];
  public final int[] current_active = new int[1];

  public synchronized void record(Statistics source)
  {
    execute_count[0] += source.execute_count[0];
    proxy_bypass_count[0] += source.proxy_bypass_count[0];
    proxy_execute_count[0] += source.proxy_execute_count[0];
    proxy_steal_count[0] += source.proxy_steal_count[0];
    mail_received_count[0] += source.mail_received_count[0];
    reflect_construct_count[0] += source.reflect_construct_count[0];
    reflect_newinstance_count[0] += source.reflect_newinstance_count[0];
    steal_count[0] += source.steal_count[0];
    allocate_overhead[0] += source.allocate_overhead[0];
    spawn_overhead[0] += source.spawn_overhead[0];
    wait_for_all_overhead[0] += source.wait_for_all_overhead[0];
  }

  @Override
  public String toString()
  {
    return "Statistics:" +
           "\nexecute_count=" +
           execute_count[0] +
           "\nproxy_bypass_count=" +
           proxy_bypass_count[0] +
           "\nproxy_execute_count=" +
           proxy_execute_count[0] +
           "\nproxy_steal_count=" +
           proxy_steal_count[0] +
           "\nmail_received_count=" +
           mail_received_count[0] +
           "\nreflect_construct_count=" +
           reflect_construct_count[0] +
           "\nreflect_newinstance_count=" +
           reflect_newinstance_count[0] +
           "\nsteal_count=" +
           steal_count[0] +
           "\nallocate_overhead=" +
           allocate_overhead[0] +
           "ms\nspawn_overhead=" +
           spawn_overhead[0] +
           "ms\nwait_for_all_overhead=" +
           wait_for_all_overhead[0] +
           "ms";
  }
}
