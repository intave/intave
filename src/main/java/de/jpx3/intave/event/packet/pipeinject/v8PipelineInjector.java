package de.jpx3.intave.event.packet.pipeinject;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

@PatchyAutoTranslation
public final class v8PipelineInjector implements PipelineInjector {
  private final InjectionService injectionService;

  public v8PipelineInjector(InjectionService injectionService) {
    this.injectionService = injectionService;
  }

  @Override
  @PatchyAutoTranslation
  public void inject(Player target) {
    EntityPlayer entityPlayer = ((CraftPlayer) target).getHandle();
    Channel channel = entityPlayer.playerConnection.networkManager.channel;
    channel.pipeline().addBefore("decoder", "intave", new v8PipelineHandler(target.getUniqueId(), injectionService));
  }

  @Override
  @PatchyAutoTranslation
  public void uninject(Player target) {
    EntityPlayer entityPlayer = ((CraftPlayer) target).getHandle();
    Channel channel = entityPlayer.playerConnection.networkManager.channel;
    ChannelPipeline pipeline = channel.pipeline();
    if(pipeline.context("intave") != null) {
      pipeline.remove("intave");
    }
  }
}
