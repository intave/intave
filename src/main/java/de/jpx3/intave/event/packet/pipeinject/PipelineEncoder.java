package de.jpx3.intave.event.packet.pipeinject;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.UUID;

public final class PipelineEncoder extends ChannelDuplexHandler {
  private final UUID id;
  private final InjectionService injectionService;

  public PipelineEncoder(UUID id, InjectionService injectionService) {
    this.id = id;
    this.injectionService = injectionService;
  }

  @Override
  public void write(ChannelHandlerContext context, Object packet, ChannelPromise channelPromise) throws Exception {
//    System.out.println(packet.getClass());


    context.write(packet, channelPromise);
  }
}
