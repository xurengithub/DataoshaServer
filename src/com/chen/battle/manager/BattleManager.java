package com.chen.battle.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chen.battle.impl.Battle;
import com.chen.battle.message.res.ResEnterRoomMessage;
import com.chen.battle.structs.BattleContext;
import com.chen.battle.structs.BattleUserInfo;
import com.chen.battle.structs.CVector2D;
import com.chen.battle.structs.EBattleServerState;
import com.chen.battle.structs.EBattleState;
import com.chen.battle.structs.EBattleType;
import com.chen.battle.structs.RoomMemberData;
import com.chen.battle.structs.SSHero;
import com.chen.battle.structs.SSPlayer;
import com.chen.match.manager.MatchManager;
import com.chen.match.structs.EBattleMatchType;
import com.chen.match.structs.MatchPlayer;
import com.chen.match.structs.MatchTeam;
import com.chen.player.structs.Player;
import com.chen.server.BattleServer;
import com.chen.server.config.BattleConfig;
import com.chen.utils.MessageUtil;

/**
 * 战斗管理器
 * @author Administrator
 *
 */
public class BattleManager 
{
	private Logger log = LogManager.getLogger(BattleManager.class);
	//战斗线程
	public ConcurrentHashMap<Long, BattleServer> mServers = new ConcurrentHashMap<Long, BattleServer>();
	public ConcurrentHashMap<Long, Battle> allBattleMap = new ConcurrentHashMap<Long, Battle>();
	private static Object obj = new Object();
	private static BattleManager manager;
	public static long battleId = 0;
	private BattleManager()
	{
		
	}
	public static BattleManager getInstance()
	{
		synchronized (obj)
		{
			if (manager == null)
			{
				manager = new BattleManager();
			}
		}
		return manager;
	}
	/**
	 * 用户请求创建匹配组队
	 * @param player
	 * @param mapId
	 * @param matchType
	 */
	public void askCreateMatchTeam(Player player,int mapId,byte matchType)
	{
		MatchTeam team = MatchManager.getInstance().UserCreateTeam(player.getMatchPlayer(),EBattleMatchType.values()[matchType], mapId);
//		MapBean bean = DataManager.getInstance().mapContainer.getMap().get(mapId);
//		if (bean == null)
//		{
//			log.error("不出在该地图");
//			return ;
//		}
		if (team == null)
		{
			log.error("创建的匹配组队不存在");
			return;
		}
		//askStartMatch(player);
	}
	/**
	 * 玩家请求开始匹配
	 * @param player
	 */
	public void askStartMatch(Player player)
	{
		int nRet = MatchManager.getInstance().TeamStartMatch(player.getMatchPlayer());
		if (nRet == 0)
		{
			log.error("匹配开始失败："+player.getId());
		}
	}
	/**
	 * 玩家请求移动
	 * @param player
	 * @param dir
	 */
	public void askMove(SSPlayer player,CVector2D dir)
	{
		SSHero hero = player.sHero;
		if (hero != null)
		{
			hero.AskMoveDir(dir);
		}
	}
	/**
	 * 玩家请求停止移动
	 * @param player
	 */
	public void askStopMove(SSPlayer player)
	{
		SSHero hero = player.sHero;
		if (hero != null)
		{
			hero.AskStopMove();
		}
	}
	/**
	 * 匹配到队友的时候
	 * @param type
	 * @param mapId
	 * @param teamList
	 */
	public void onBattleMached(EBattleMatchType type,int mapId,HashMap<Integer, Vector<MatchTeam>> teamList)
	{
		HashMap<Integer, Player> userListMap = new HashMap<Integer, Player>();
		MatchTeam team = null;
		MatchPlayer player = null;
		for (int i=0;i<teamList.keySet().size();i++)
		{
			Iterator<MatchTeam> iter = teamList.get(i).iterator();
			int index = 0;
			while (iter.hasNext()) {
				team = (MatchTeam) iter.next();
				//停止搜索界面
				team.search(false);
				Iterator<MatchPlayer> iterator = team.getPlayers().iterator();
				while (iterator.hasNext()) {
					player = iterator.next();
					userListMap.put(index++, player.getPlayer());
				}
			}
		}
		this.onBattleMached(userListMap, mapId, type);
	}
	public void onBattleMached(HashMap<Integer, Player> listMap,int mapId,EBattleMatchType type)
	{
		Battle battle = new Battle(type,EBattleType.eBattleType_Match,this.generateBattleId(),mapId,listMap);
		allBattleMap.put(battle.getBattleId(), battle);
		battle.start();
	}
	public void createBattle(Map<Integer, Player> userMap,long battleId,byte matchType,int mapId)
	{
		boolean isCreateSucc = false;
		BattleContext battle = null;
		if (userMap == null || userMap.size() == 0)
		{
			log.error("进入战斗房间消息为空");
			return ;
		}
		if (this.mServers.get(battleId) != null)
		{
			log.error("已经存在该战斗，不需要重新创建");
			return ;
		}
		List<RoomMemberData> listData = new ArrayList<>();
		do 
		{
			List<BattleConfig> configs = new ArrayList<BattleConfig>();	
			battle = new BattleContext(EBattleType.values()[matchType],battleId,configs);
			//加载地图
			//设置每个人的信息SSUser

			for (int i=0;i<userMap.size();i++)
			{
				 BattleUserInfo info = new BattleUserInfo();
				 Player p = userMap.get(i);	
					RoomMemberData data = new RoomMemberData();
					data.playerId = p.getId();
					data.name = p.getName();
					data.level = p.getLevel();
					data.icon = p.getIcon();
					data.isReconnecting = (byte)0;
					listData.add(data);
				 SSPlayer player = new SSPlayer(p);			 
				 for (int j=0;j<p.getHeroList().size();j++)
				 {
					 player.addCanUseHero(p.getHeroList().get(j).getHeroId());
				 }
				 player.bIfConnect = true;
				 player.battleId = battleId;
				 info.sPlayer = player;
				 battle.getM_battleUserInfo()[i] = info;
			}
			mServers.put(battle.getBattleId(),battle);
			isCreateSucc = true;
		}while(false);
		if (isCreateSucc == false && battle != null)
		{
			battle = null;
			allBattleMap.remove(battleId);
			return;
		}
		Battle cBattle = allBattleMap.get(battleId);
		cBattle.onCreate();
		ResEnterRoomMessage msg = new ResEnterRoomMessage();
		msg.battleId = battleId;
		msg.m_nMapId = mapId;
		msg.m_btGameType = matchType;
		msg.m_nTimeLimit = BattleContext.timeLimit;
		//发送开始战斗的请求
		for (int i=0; i<userMap.size(); i++)
		{
			Player player =  userMap.get(i);
			player.getBattleInfo().setBattleId(battleId);
			//data.canUseHeroList.addAll(battle.getM_battleUserInfo()[i].sPlayer.canUserHeroList);
			msg.canUseHeroList.addAll(battle.getM_battleUserInfo()[i].sPlayer.canUserHeroList);
			msg.m_oData = listData;
			MessageUtil.tell_player_message(player, msg);
			msg.canUseHeroList.clear();
		}
		battle.setBattleState(EBattleServerState.eSSBS_SelectHero, false);
		new Thread(battle).start();
	}
	private long generateBattleId()
	{
		return ++battleId;		
	}
	public BattleContext getBattleContext(Player player)
	{
		if (player.getBattleInfo().getBattleState() != EBattleState.eBattleState_Play)
		{
			return null;
		}
		if (player.getBattleInfo().getBattleId() <= 0)
		{
			return null;
		}
		return (BattleContext) mServers.get(player.getBattleInfo().getBattleId());
	}
	
	public void OnBattleHeartBeat()
	{
		
	}
}
