package com.gy_mod.gy_trinket.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class ReflectShieldParticle extends TextureSheetParticle {
    private final float lifeTime;
    private final SpriteSet sprites;

    protected ReflectShieldParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        
        this.sprites = sprites;
        this.friction = 1F;
        this.gravity = 0.0F;
        this.lifeTime = 30.0F;
        this.lifetime = (int) this.lifeTime;
        
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.quadSize = (float) (0.01 + speed * 0.05);
        
        this.hasPhysics = false;
        
        float brightness = 12.0F / 15.0F;
        this.rCol = 1.0F * brightness;
        this.gCol = 1.0F * brightness;
        this.bCol = 1.0F * brightness;
        this.alpha = 1.0F;
    }

    @Override
    public void tick() {
        this.xd *= 0.85;
        this.yd *= 0.85;
        this.zd *= 0.85;
        
        super.tick();
        
        this.setSpriteFromAge(this.sprites);
        
        float progress = (float) this.age / this.lifeTime;
        
        this.quadSize = Mth.lerp(progress, 0.2F, 0.0F);
        
        this.alpha = Mth.lerp(progress, 1.0F, 0.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }
        
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            ReflectShieldParticle particle = new ReflectShieldParticle(level, x, y, z, dx, dy, dz, this.sprites);
            particle.setSpriteFromAge(this.sprites);
            return particle;
        }
    }
}