package com.chen.battle.structs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chen.battle.message.res.ResBattleTipMessage;
import com.chen.battle.message.res.ResEnterSceneMessage;
import com.chen.battle.message.res.ResGamePrepareMessage;
import com.chen.battle.message.res.ResSceneLoadedMessage;
import com.chen.battle.message.res.ResSelectHeroMessage;
import com.chen.message.Message;
import com.chen.move.manager.SSMoveManager;
import com.chen.move.struct.ColVector;
import com.chen.move.struct.EAskStopMoveType;
import com.chen.player.structs.Player;
import com.chen.server.BattleServer;
import com.chen.server.Server;
import com.chen.server.config.BattleConfig;
import com.chen.server.thread.ServerThread;
import com.chen.utils.MessageUtil;

public class BattleContext extends BattleServer
{
	private Logger log = LogManager.getLogger(BattleContext.class);
	private EBattleType battleType;
	private EBattleServerState battleState = EBattleServerState.eSSBS_SelectHero;
	private long battleId;
	private long battleStateTime;
	private BattleUserInfo[] m_battleUserInfo = new BattleUserInfo[maxMemberCount];
	
	public Map<Long, SSGameUnit> gameObjectMap = new HashMap<>();
	public Set<EBattleTipType> tipSet = new HashSet<>();
	public SSMoveManager moveManager;

	public static final int maxMemberCount = 6; 
	public static final int timeLimit = 200000;
	public static final int prepareTimeLimit = 5000;
	public static final int loadTimeLimit = 100000;
	public EBattleType getBattleType() {
		return battleType;
	}
	public void setBattleType(EBattleType battleType) {
		this.battleType = battleType;
	}
	public long getBattleId() {
		return battleId;
	}
	public void setBattleId(long battleId) {
		this.battleId = battleId;
	}
	public EBattleServerState getBattleState() {
		return battleState;
	}
	public void setBattleState(EBattleServerState battleState) {
		this.battleState = battleState;
	}
	public BattleUserInfo[] getM_battleUserInfo() {
		return m_battleUserInfo;
	}
	public void setM_battleUserInfo(BattleUserInfo[] m_battleUserInfo) {
		this.m_battleUserInfo = m_battleUserInfo;
	}
	public BattleContext(EBattleType type, long battleId,List<BattleConfig> configs)
	{
		super("战斗-"+battleId,configs);
		this.battleId = battleId;
		this.battleType = type;
		this.moveManager = new SSMoveManager();
	}
	
	@Override
	protected void init() 
	{
         System.out.println("BattleContent:Init");
	}
	@Override
	public void run()
	{
		super.run();
		new Timer("Time out").schedule(new TimerTask() {
			@Override
			public void run() 
			{			
				BattleContext.this.checkLoadingTimeout();
				BattleContext.this.checkPrepareTimeout();
				BattleContext.this.checkSelectHeroTimeout();
				BattleContext.this.DoPlayHeartBeat();
			}
		},100,100);
		//((ServerThread)this.thread_pool.get(Server.DEFAULT_MAIN_THREAD)).addTimeEvent(event);
	}
	public void EnterBattleState(Player player)
	{
		boolean isReconnect = player.isReconnect();
		if (isReconnect)
		{
			//通知重新连接信息
		}
		log.info("玩家"+player.getId()+"确认加入战斗房间，当前战斗状态:"+battleState.toString());
		//以后再扩展开选择符文等
	}
	/**
	 * 玩家确认选择该英雄
	 * @param player
	 * @param heroId
	 */
	public void AskSelectHero(Player player,int heroId)
	{
		BattleUserInfo info = getUserBattleInfo(player);
		info.selectedHeroId = heroId;
		info.bIsHeroChoosed = true;
		ResSelectHeroMessage msg = new ResSelectHeroMessage();
		msg.playerId = player.getId();
		msg.heroId = heroId;
		MessageUtil.tell_battlePlayer_message(this,msg);
	}
	/**
	 * 玩家发送加载完成消息
	 */
	public void EnsurePlayerLoaded(Player player)
	{
		BattleUserInfo data = this.getUserBattleInfo(player);
		data.bIsLoadedComplete = true;
		ResSceneLoadedMessage msg = new ResSceneLoadedMessage();
		msg.m_playerId = player.getId();
		MessageUtil.tell_battlePlayer_message(this, msg);
	}
	public void checkSelectHeroTimeout()
	{
		if (this.battleState != EBattleServerState.eSSBS_SelectHero)
		{
			return ;
		}
		boolean ifAllUserSelect = true;
		for (int i=0; i<maxMemberCount; i++)
		{
			if (this.m_battleUserInfo[i] != null)
			{
				if (this.m_battleUserInfo[i].bIsHeroChoosed == false)
				{
					ifAllUserSelect = false;
					break;
				}
			}
		}
		//等待时间结束
		if (ifAllUserSelect || (System.currentTimeMillis() - this.battleStateTime) >= timeLimit)
		{
			for (int i = 0; i < maxMemberCount; i++) {
				if (this.m_battleUserInfo[i] != null)
				{
					if (false == this.m_battleUserInfo[i].bIsHeroChoosed) {
						//如果还没有选择神兽，就随机选择一个
						if (this.m_battleUserInfo[i].selectedHeroId == -1)
						{
							this.m_battleUserInfo[i].selectedHeroId = randomPickHero(this.m_battleUserInfo[i].sPlayer.canUserHeroList);
						}
						this.m_battleUserInfo[i].bIsHeroChoosed = true;
						//然后将选择该神兽的消息广播给其他玩家
						ResSelectHeroMessage msg = new ResSelectHeroMessage();
						msg.heroId = this.m_battleUserInfo[i].selectedHeroId;
						msg.playerId = this.m_battleUserInfo[i].sPlayer.player.getId();
						MessageUtil.tell_battlePlayer_message(this, msg);
					}
				}
			}
			//选择神兽阶段结束，改变状态，进入准备状态
			setBattleState(EBattleServerState.eSSBS_Prepare,true);
		}
	}
	public void checkLoadingTimeout()
	{
		if (this.battleState != EBattleServerState.eSSBS_Loading)
		{
			return ;
		}
		boolean bIfAllPlayerConnect = true;
		//时间未到，则检查是否所有玩家已经连接
		if (System.currentTimeMillis() - this.battleStateTime < loadTimeLimit)
		{
			for (int i=0;i<this.m_battleUserInfo.length;i++)
			{
				if (this.m_battleUserInfo[i] != null)
				{
					if (this.m_battleUserInfo[i].bIsLoadedComplete == false)
					{
						bIfAllPlayerConnect = false;
						break;
					}
				}
			}
		}
		if (bIfAllPlayerConnect == false)
		{
			return;
		}
		//加载静态的配置文件
		this.LoadMapConfigNpc();
		//然后创建神兽
		for (int i=0;i<this.m_battleUserInfo.length;i++)
		{
			if (this.m_battleUserInfo[i] == null)
			{
				continue;
			}
			SSPlayer user = this.m_battleUserInfo[i].sPlayer;
			CVector2D bornPos = new CVector2D(0, 0);//这里需要通过配置文件加载
			CVector2D dir = new CVector2D(1, 0);
			SSHero hero = null;
			hero = AddHero(user.player.getId(), bornPos, dir, user, this.m_battleUserInfo[i].selectedHeroId);
			this.m_battleUserInfo[i].sHero = hero;
			//通知玩家游戏开始战斗倒计时
			BoradTipsByType(EBattleTipType.eTips_ObjAppear, this.m_battleUserInfo[i].sPlayer.player);
		}
		
		this.PostStartGameMsg();
		this.setBattleState(EBattleServerState.eSSBS_Playing);
	}
	public void checkPrepareTimeout()
	{
		if (this.battleState != EBattleServerState.eSSBS_Prepare)
		{
			return ;
		}
		if (System.currentTimeMillis() - this.battleStateTime > prepareTimeLimit)
		{
			this.setBattleState(EBattleServerState.eSSBS_Loading, true);
		}
	}
	public void DoPlayHeartBeat()
	{
		if (this.battleState != EBattleServerState.eSSBS_Playing)
		{
			return;
		}
		this.moveManager.OnHeartBeat();
	}
	public void BoradTipsByType(EBattleTipType type,Player player)
	{
		boolean flag = false;
		if (!this.tipSet.contains(type))
		{
			this.tipSet.add(type);
			flag = true;
		}
		if (flag)
		{
			ResBattleTipMessage message = new ResBattleTipMessage();
			switch (type) {
			case eTips_ObjAppear:
				message.tipCode = 100;
				break;
			case eTips_Gas:
				message.tipCode = 101;
				break;
			default:
				break;
			}
			MessageUtil.tell_player_message(player, message);
		}	
	}
	/**
	 * 改变游戏状态
	 * @param state
	 * @param isSendToClient
	 */
	public void setBattleState(EBattleServerState state,boolean isSendToClient)
	{
		this.battleState = state;
		this.battleStateTime = System.currentTimeMillis();
		if (isSendToClient)
		{
			switch (state) {
			case eSSBS_Prepare:
				//通知客户端开始进入准备状态
				ResGamePrepareMessage pre_msg = new ResGamePrepareMessage();
				pre_msg.setTimeLimit(prepareTimeLimit);
				MessageUtil.tell_battlePlayer_message(this, pre_msg);
				break;
			case eSSBS_Loading:
				//通知客户端开始加载场景
				ResEnterSceneMessage scene_msg = new ResEnterSceneMessage();
				MessageUtil.tell_battlePlayer_message(this, scene_msg);
				break;			
			default:
				break;
			}
		}
	}
	/**
	 * 请求开始移动
	 * @param player
	 * @param _dir
	 * @return
	 */
	public boolean AskMoveDir(SSGameUnit player,CVector2D _dir)
	{
		ColVector dir = new ColVector(_dir.x, _dir.y);
		return moveManager.AskStartMoveDir(player, dir);
	}
	/**
	 * 请求停止移动
	 * @param player
	 * @return
	 */
	public boolean AskStopMoveDir(SSGameUnit player)
	{
		return moveManager.AskStopMoveObject(player, EAskStopMoveType.Dir);
	}
	/**
	 * 加载地图配置
	 */
	public void LoadMapConfigNpc()
	{
//		map = new SSMap();
//		map.Init(0, "server-config/map-config.xml");
	}	
	public SSHero AddHero(Long playerId,CVector2D pos,CVector2D dir,SSPlayer user,int heroId)
	{
		//取得英雄配置表加载基础数据
		SSHero hero = new SSHero(playerId,this);
		//hero.LOadHeroConfig
		user.sHero = hero;
		hero.BeginActionIdle(false);
		hero.bornPos = pos;
		hero.ResetAI();
		this.EnterBattle(hero, pos, dir);
		//加载被动技能
		return hero;
	}
	public void EnterBattle(SSGameUnit go,CVector2D pos, CVector2D dir)
	{
		SSGameUnit unit = gameObjectMap.get(go.id);
		if (unit != null)
		{
			log.debug("游戏场景中已经存在该物体");
			return;
		}
		go.curActionInfo.pos = pos;
		//go.expire = false;
		gameObjectMap.put(go.id, go);
		go.curActionInfo.dir = dir;
		go.enterBattleTime = System.currentTimeMillis();
		if (go.IfCanImpact())
		{
			this.AddMoveObject(go);
		}
	}
	public void AddMoveObject(SSMoveObject obj)
	{
		this.moveManager.AddMoveObject(obj);
	}
	public void SyncState(SSGameUnit obj)
	{
		if (obj == null)
		{
			log.error("不存在英雄");
			return;
		}
		Message message = obj.ConstructObjStateMessage();
		MessageUtil.tell_battlePlayer_message(this, message);
	}
	/**
	 * 取得随机神兽
	 * @param pickHeroList
	 * @param camType
	 * @return
	 */
	private int randomPickHero(Set<Integer> pickHeroList)
	{
		List<Integer> canChooseList = new ArrayList<Integer>();
		if (pickHeroList == null || pickHeroList.size() == 0)
		{
			System.out.println("没有英雄可以选择");
		}
		for (int heroId : pickHeroList) 
		{
			canChooseList.add(heroId);
		}
		return canChooseList.get((int) (Math.random()*canChooseList.size()));		
	}
	/**
	 * 根据玩家取得玩家数据
	 * @param player
	 * @return
	 */
	public BattleUserInfo getUserBattleInfo(Player player)
	{
		if (player == null)
		{
			return null;
		}
		for (int i=0; i<this.m_battleUserInfo.length; i++)
		{
			if (this.m_battleUserInfo[i] == null)
			{
				continue;
			}
			if (this.m_battleUserInfo[i].sPlayer.player.getId() == player.getId())
			{
				return this.m_battleUserInfo[i];
			}
		}
		return null;
	}
	private void PostStartGameMsg()
	{
		for (int i=0; i<this.m_battleUserInfo.length; i++)
		{
			if (this.m_battleUserInfo[i] == null)
			{
				continue;
			}	
			if (this.m_battleUserInfo[i].sPlayer != null)
			{
				this.m_battleUserInfo[i].sHero.SendAppearMessage();
			}
			//发送每个玩家的英雄信息
			//然后通知客户端加载模型
		}
	}
}
