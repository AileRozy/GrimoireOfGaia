package gaia.entity.monster;

import javax.annotation.Nullable;

import gaia.GaiaConfig;
import gaia.entity.EntityAttributes;
import gaia.entity.EntityMobPassiveDay;
import gaia.entity.ai.EntityAIGaiaAttackRangedBow;
import gaia.entity.ai.EntityAIGaiaBreakDoor;
import gaia.entity.ai.EntityAIGaiaValidateTargetPlayer;
import gaia.entity.ai.GaiaIRangedAttackMob;
import gaia.entity.ai.Ranged;
import gaia.init.GaiaItems;
import gaia.items.ItemShard;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.PotionTypes;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings({ "squid:MaximumInheritanceDepth", "squid:S2160" })
public class EntityGaiaDwarf extends EntityMobPassiveDay implements GaiaIRangedAttackMob {
	private static final String MOB_TYPE_TAG = "MobType";

	private EntityAIGaiaAttackRangedBow aiArrowAttack = new EntityAIGaiaAttackRangedBow(this, EntityAttributes.ATTACK_SPEED_2, 20, 15.0F);
	private EntityAIAttackMelee aiAttackOnCollide = new EntityAIAttackMelee(this, EntityAttributes.ATTACK_SPEED_2, true);
	private final EntityAIGaiaBreakDoor breakDoor = new EntityAIGaiaBreakDoor(this);

	private static final DataParameter<Integer> SKIN = EntityDataManager.createKey(EntityGaiaDwarf.class, DataSerializers.VARINT);
	private static final DataParameter<Boolean> HOLDING_BOW = EntityDataManager.createKey(EntityGaiaDwarf.class, DataSerializers.BOOLEAN);
	private static final ItemStack TIPPED_ARROW_CUSTOM = PotionUtils.addPotionToItemStack(new ItemStack(Items.TIPPED_ARROW), PotionTypes.SLOWNESS);
	private static final ItemStack TIPPED_ARROW_CUSTOM_2 = PotionUtils.addPotionToItemStack(new ItemStack(Items.TIPPED_ARROW), PotionTypes.WEAKNESS);

	private int mobClass;

	private boolean canSpawnLevel3;
	private boolean spawned;
	private int spawnLevel3;
	private int spawnLevel3Chance;

	public EntityGaiaDwarf(World worldIn) {
		super(worldIn);

		setSize(0.5F, 1.5F);
		experienceValue = EntityAttributes.EXPERIENCE_VALUE_2;
		stepHeight = 1.0F;

		mobClass = 0;
		spawnLevel3 = 0;
		spawnLevel3Chance = 0;

		if (!worldIn.isRemote) {
			setCombatTask();
		}
	}

	@Override
	protected void initEntityAI() {
		tasks.addTask(0, new EntityAISwimming(this));
		tasks.addTask(3, new EntityAIWander(this, 1.0D));
		tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
		tasks.addTask(4, new EntityAILookIdle(this));
		targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
		targetTasks.addTask(2, new EntityAIGaiaValidateTargetPlayer(this));
	}

	/**
	 * Sets or removes EntityAIBreakDoor task
	 */
	public void setBreakDoorsAItask(boolean enabled) {
		((PathNavigateGround) this.getNavigator()).setBreakDoors(enabled);

		if (enabled) {
			this.tasks.addTask(1, this.breakDoor);
		} else {
			this.tasks.removeTask(this.breakDoor);
		}
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(EntityAttributes.MAX_HEALTH_2);
		getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(EntityAttributes.FOLLOW_RANGE);
		getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(EntityAttributes.MOVE_SPEED_2);
		getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(EntityAttributes.ATTACK_DAMAGE_2);
		getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(EntityAttributes.RATE_ARMOR_2);
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float damage) {
		if (damage > EntityAttributes.BASE_DEFENSE_2) {
			if (canSpawnLevel3) {
				spawnLevel3Chance += (int) (GaiaConfig.SPAWN.spawnLevel3Chance * 0.05);
			}
		}

		if (hasShield()) {
			return !(source instanceof EntityDamageSourceIndirect) && super.attackEntityFrom(source, Math.min(damage, EntityAttributes.BASE_DEFENSE_2));
		} else {
			return super.attackEntityFrom(source, Math.min(damage, EntityAttributes.BASE_DEFENSE_2));
		}
	}

	private boolean hasShield() {
		ItemStack itemstack = this.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);

		if (itemstack.getItem() == Items.SHIELD || itemstack.getItem() == GaiaItems.SHIELD_PROP) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void knockBack(Entity entityIn, float strength, double xRatio, double zRatio) {
		super.knockBack(xRatio, zRatio, EntityAttributes.KNOCKBACK_2);
	}

	@Override
	public boolean attackEntityAsMob(Entity entityIn) {
		if (super.attackEntityAsMob(entityIn)) {
			if (getMobType() == 1 && entityIn instanceof EntityLivingBase) {
				byte byte0 = 0;

				if (world.getDifficulty() == EnumDifficulty.NORMAL) {
					byte0 = 10;
				} else if (world.getDifficulty() == EnumDifficulty.HARD) {
					byte0 = 20;
				}

				if (byte0 > 0) {
					((EntityLivingBase) entityIn).addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, byte0 * 20));
					((EntityLivingBase) entityIn).addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, byte0 * 20));
				}
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean isAIDisabled() {
		return false;
	}

	@Override
	public void onLivingUpdate() {
		/* LEVEL 3 SPAWN DATA */
		if ((GaiaConfig.SPAWN.spawnLevel3 && (GaiaConfig.SPAWN.spawnLevel3Chance != 0)) && !canSpawnLevel3) {
			canSpawnLevel3 = true;
		}

		if (canSpawnLevel3) {
			if (getHealth() < EntityAttributes.MAX_HEALTH_2 * 0.25F && getHealth() > 0.0F && !spawned) {

				if (spawnLevel3Chance > (int) (GaiaConfig.SPAWN.spawnLevel3Chance * 0.5)) {
					spawnLevel3Chance = (int) (GaiaConfig.SPAWN.spawnLevel3Chance * 0.5);
				}

				if ((rand.nextInt(GaiaConfig.SPAWN.spawnLevel3Chance - spawnLevel3Chance) == 0 || rand.nextInt(1) > 0)) {
					spawnLevel3 = 1;
				}

				spawned = true;
			}
		}

		if (spawnLevel3 == 1) {
			world.setEntityState(this, (byte) 10);

			attackEntityFrom(DamageSource.GENERIC, EntityAttributes.MAX_HEALTH_2 * 0.01F);
		}
		/* LEVEL 3 SPAWN DATA */

		super.onLivingUpdate();
	}

	private void SetSpawn(byte id) {
		EntityGaiaValkyrie valyrie;

		if (id == 1) {
			explode();

			valyrie = new EntityGaiaValkyrie(world);
			valyrie.setLocationAndAngles(posX, posY, posZ, rotationYaw, 0.0F);
			valyrie.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(valyrie)), null);
			world.spawnEntity(valyrie);
		}
	}

	/* CLASS TYPE */
	@Override
	public void setItemStackToSlot(EntityEquipmentSlot par1, ItemStack par2ItemStack) {
		super.setItemStackToSlot(par1, par2ItemStack);
		if (!world.isRemote && par1.getIndex() == 0) {
			setCombatTask();
		}
	}

	private void setCombatTask() {
		tasks.removeTask(aiAttackOnCollide);
		tasks.removeTask(aiArrowAttack);
		ItemStack itemstack = getHeldItemMainhand();
		if (itemstack.getItem() == Items.BOW) {
			tasks.addTask(2, aiArrowAttack);
		} else {
			tasks.addTask(2, aiAttackOnCollide);
		}
	}

	public int getTextureType() {
		return dataManager.get(SKIN);
	}

	private void setTextureType(int par1) {
		dataManager.set(SKIN, par1);
	}

	private int getMobType() {
		return dataManager.get(SKIN);
	}

	private void setMobType(int par1) {
		dataManager.set(SKIN, par1);
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound compound) {
		super.writeEntityToNBT(compound);
		compound.setByte(MOB_TYPE_TAG, (byte) getMobType());
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound compound) {
		super.readEntityFromNBT(compound);
		if (compound.hasKey(MOB_TYPE_TAG)) {
			byte b0 = compound.getByte(MOB_TYPE_TAG);
			setMobType(b0);
		}

		setCombatTask();
	}
	/* CLASS TYPE */

	/* ARCHER DATA */
	@Override
	public boolean canAttackClass(Class<? extends EntityLivingBase> cls) {
		return super.canAttackClass(cls) && cls != EntityGaiaDwarf.class;
	}

	public void attackEntityWithRangedAttack(EntityLivingBase target, float distanceFactor) {
		Ranged.rangedAttack(target, this, distanceFactor);
	}

	@Override
	public void setSwingingArms(boolean swingingArms) {
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		dataManager.register(SKIN, 0);
		dataManager.register(HOLDING_BOW, Boolean.FALSE);
	}

	@SideOnly(Side.CLIENT)
	public boolean isHoldingBow() {
		return dataManager.get(HOLDING_BOW);
	}

	public void setHoldingBow(boolean swingingArms) {
		dataManager.set(HOLDING_BOW, swingingArms);
	}
	/* ARCHER DATA */

	private void explode() {
		if (!this.world.isRemote) {
			boolean flag = net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(this.world, this);
			int explosionRadius = 2;

			this.dead = true;
			this.world.createExplosion(this, this.posX, this.posY, this.posZ, (float) explosionRadius, flag);
			this.setDead();
		}
	}

	@Override
	protected void dropFewItems(boolean wasRecentlyHit, int lootingModifier) {
		if (wasRecentlyHit) {
			int drop = rand.nextInt(3 + lootingModifier);

			if (mobClass == 1) {
				for (int i = 0; i < drop; ++i) {
					dropItem(Items.ARROW, 1);
				}
			} else {
				for (int i = 0; i < drop; ++i) {
					dropItem(Items.IRON_NUGGET, 1);
				}
			}

			// Nuggets/Fragments
			int dropNugget = rand.nextInt(3) + 1;

			for (int i = 0; i < dropNugget; ++i) {
				dropItem(Items.GOLD_NUGGET, 1);
			}

			if (GaiaConfig.OPTIONS.additionalOre) {
				int dropNuggetAlt = rand.nextInt(3) + 1;

				for (int i = 0; i < dropNuggetAlt; ++i) {
					ItemShard.dropNugget(this, 5);
				}
			}

			// Rare
			if ((rand.nextInt(EntityAttributes.RATE_RARE_DROP) == 0 || rand.nextInt(1 + lootingModifier) > 0)) {
				if (mobClass == 1) {
					switch (rand.nextInt(3)) {
					case 0:
						dropItem(GaiaItems.BOX_GOLD, 1);
					case 1:
						dropItem(GaiaItems.BAG_BOOK, 1);
					case 2:
						dropItem(GaiaItems.BAG_ARROW, 1);
					}
				} else {
					switch (rand.nextInt(2)) {
					case 0:
						dropItem(GaiaItems.BOX_GOLD, 1);
					case 1:
						dropItem(GaiaItems.BAG_BOOK, 1);
					}
				}
			}
		}

		// Boss
		if (spawnLevel3 == 1) {
			SetSpawn((byte) 1);
		}
	}

	@Override
	protected void dropEquipment(boolean wasRecentlyHit, int lootingModifier) {
	}

	@Override
	public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData livingdata) {
		IEntityLivingData ret = super.onInitialSpawn(difficulty, livingdata);

		if (world.rand.nextInt(2) == 0) {
			tasks.addTask(2, aiArrowAttack);

			ItemStack bowCustom = new ItemStack(Items.BOW);
			setItemStackToSlot(EntityEquipmentSlot.MAINHAND, bowCustom);
			bowCustom.addEnchantment(Enchantments.PUNCH, 1);
			if (world.rand.nextInt(2) == 0) {
				if (world.rand.nextInt(2) == 0) {
					setItemStackToSlot(EntityEquipmentSlot.OFFHAND, TIPPED_ARROW_CUSTOM);
				} else {
					setItemStackToSlot(EntityEquipmentSlot.OFFHAND, TIPPED_ARROW_CUSTOM_2);
				}
			}

			setTextureType(1);
			mobClass = 1;
		} else {
			tasks.addTask(2, aiAttackOnCollide);

			setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(GaiaItems.WEAPON_PROP_AXE_STONE));
			setEnchantmentBasedOnDifficulty(difficulty);

			if (world.rand.nextInt(2) == 0) {
				ItemStack shield = new ItemStack(GaiaItems.SHIELD_PROP, 1, 1);
				setItemStackToSlot(EntityEquipmentSlot.OFFHAND, shield);
			}

			setMobType(1);
			getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.25D);
			setTextureType(0);
			mobClass = 0;
		}

		if (GaiaConfig.SPAWN.spawnLevel3 && (GaiaConfig.SPAWN.spawnLevel3Chance != 0)) {
			canSpawnLevel3 = true;
		}

		setBreakDoorsAItask(true);

		return ret;
	}

	/* SPAWN CONDITIONS */
	@Override
	public int getMaxSpawnedInChunk() {
		return 2;
	}

	@Override
	public boolean getCanSpawnHere() {
		return posY > 60.0D && super.getCanSpawnHere();
	}
	/* SPAWN CONDITIONS */
}
