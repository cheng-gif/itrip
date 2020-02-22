package cn.itrip.auth.service;

import cn.itrip.beans.pojo.ItripUser;

public interface UserService {

    /**
     * 使用手机号创建用户账号
     * @param user
     * @throws Exception
     */
    public void itriptxCreateUserByPhone(ItripUser user) throws Exception;

    /**
     * 短信验证手机号
     * @param phoneNumber
     * @return
     * @throws Exception
     */
    public boolean validatePhone(String phoneNumber,String code) throws Exception;

    public ItripUser findByUsername(String username) throws Exception;

    /**
     * 修改激活码
     * @param user
     * @throws Exception
     */
    public void updateUser(ItripUser user) throws Exception;

    /**
     * 邮箱激活
     * @param email 用户注册油箱
     * @param code 激活码
     * @return
     * @throws Exception
     */
    public boolean activate(String email,String code) throws Exception;

    public void itriptxCreateUser(ItripUser user) throws Exception;

    public ItripUser login(String userCode,String userPassword) throws  Exception;
}
