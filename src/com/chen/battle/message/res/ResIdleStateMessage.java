package com.chen.battle.message.res;

import org.apache.mina.core.buffer.IoBuffer;

import com.chen.message.Message;

public class ResIdleStateMessage extends Message
{
	public long playerId;
	public int posX;
	public int posY;	
	public int dirX;
	public int dirY;
	@Override
	public int getId() {
		// TODO Auto-generated method stub
		return 1024;
	}

	@Override
	public String getQueue() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getServer() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean read(IoBuffer buffer) 
	{
		
		return true;
	}

	@Override
	public boolean write(IoBuffer buffer) 
	{
		writeLong(buffer, playerId);
		writeInt(buffer, posX);
		writeInt(buffer, posY);
		writeInt(buffer, dirX);
		writeInt(buffer, dirY);
		return true;
	}
	
}
