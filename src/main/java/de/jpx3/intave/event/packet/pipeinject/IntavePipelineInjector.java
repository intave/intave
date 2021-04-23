package de.jpx3.intave.event.packet.pipeinject;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

@PatchyAutoTranslation
public final class IntavePipelineInjector implements PipelineInjector {
  private final InjectionService injectionService;
  private final static String PIPELINE_DECODER_NAME = "intave_decoder";
  private final static String PIPELINE_ENCODER_NAME = "intave_encoder";

  public IntavePipelineInjector(InjectionService injectionService) {
    this.injectionService = injectionService;
  }

  @Override
  @PatchyAutoTranslation
  public void inject(Player target) {
    EntityPlayer entityPlayer = ((CraftPlayer) target).getHandle();
    Channel channel = entityPlayer.playerConnection.networkManager.channel;
//    channel.pipeline().forEach(System.out::println);
//    System.out.println("Inject");
    channel.pipeline().addBefore("decoder", PIPELINE_DECODER_NAME, new PipelineDecoder(target.getUniqueId(), injectionService));
    channel.pipeline().addAfter("encoder", PIPELINE_ENCODER_NAME, new PipelineEncoder(target.getUniqueId(), injectionService));
//    System.out.println("Done");
//    channel.pipeline().forEach(System.out::println);
  }

  @Override
  @PatchyAutoTranslation
  public void uninject(Player target) {
    EntityPlayer entityPlayer = ((CraftPlayer) target).getHandle();
    Channel channel = entityPlayer.playerConnection.networkManager.channel;
    ChannelPipeline pipeline = channel.pipeline();
    if(pipeline.context(PIPELINE_DECODER_NAME) != null) {
      pipeline.remove(PIPELINE_DECODER_NAME);
    }
    if(pipeline.context(PIPELINE_ENCODER_NAME) != null) {
      pipeline.remove(PIPELINE_ENCODER_NAME);
    }
  }
}
