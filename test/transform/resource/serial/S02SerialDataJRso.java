
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;

import cn.jmicro.api.choreography.AgentInfoJRso;
import cn.jmicro.api.monitor.OneLogJRso;
import cn.jmicro.api.net.IReq;
import cn.jmicro.api.net.IResp;
import cn.jmicro.api.registry.UniqueServiceMethodKeyJRso;
import cn.jmicro.api.sysstatis.SystemStatisJRso;
import cn.jmicro.api.utils.TimeUtils;

import java.io.File;
import java.time.LocalDateTime;

@lombok.Serial public class S02SerialDataJRso{

	//@BsonId
	//private Long id;
	
	//private LocalDateTime lastLoginTime;
	
	//private S02SerialDataJRso agentInfo;
	
	//private String[] depIds;
	
	//private String[] intIds;
	
	//参数名称
	//private String name;
	
	//参数值
	private Object val;
	
	private String[] depIds;
	
	//参数描述
	//private String desc;
	
	//参数值类型
	//private byte type;
	
	//private String clazz;
}
