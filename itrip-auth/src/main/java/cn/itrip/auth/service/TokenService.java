package cn.itrip.auth.service;

import cn.itrip.auth.exception.TokenValidationFailedException;
import cn.itrip.beans.pojo.ItripUser;

public interface TokenService {
    /**
     * 会话超时时间
     */
    public final static int SESSION_TIMEOUT=2*60*60;//默认2h
    /**
     * 置换保护时间
     */
    public final static int REPLACEMENT_PROTECTION_TIMEOUT=60*60;//默认1h
    /**
     * 旧token延迟过期时间
     */
    public final static int REPLACEMENT_DELAY=2*60;//默认2min

    //生成token
    public String generateToken(String agent, ItripUser itripUser);

    //保存token
    public void save(String token,ItripUser itripUser);

    /**
     * 从redis获取用户信息
     * @param tokenID
     * @return
     */
    public ItripUser load(String token);
    /**
     * 删除token
     * @param tokenID
     */
    public void delete(String token);

    /**
     * 置换Token <BR/>
     *  1、首先要判断token是否有效 	<BR/>
     *  2、生成token后的1个小时内不允许置换 	<BR/>
     *  3、置换token时，需要生成新token，并且旧token不能立即失效，应设置为置换后的时间延长2分钟 <BR/>
     *  4、兼容手机端和PC端 <BR/>
     * @param agent	User-Agent
     * @param token	旧的token
     * @return 新的token
     * @throws TokenValidationFailedException
     */
    public String replaceToken(String agent,String token) throws TokenValidationFailedException;

    /**
     * 验证token是否有效
     * @param agent
     * @param token
     * @return
     */
    public boolean validate(String agent,String token);
}
