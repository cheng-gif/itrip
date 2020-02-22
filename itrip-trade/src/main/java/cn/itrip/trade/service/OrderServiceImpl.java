package cn.itrip.trade.service;

import java.io.BufferedReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import cn.itrip.beans.pojo.ItripHotelOrder;
import cn.itrip.beans.pojo.ItripTradeEnds;
import cn.itrip.common.EmptyUtils;
import cn.itrip.common.SystemConfig;
import cn.itrip.dao.hotelorder.ItripHotelOrderMapper;
import cn.itrip.dao.tradeends.ItripTradeEndsMapper;

/**
 * 订单支付处理实现
 * @author hduser
 *
 */
@Service("orderService")
public class OrderServiceImpl implements OrderService {

	private Logger logger=Logger.getLogger(OrderServiceImpl.class);
	@Resource
	private ItripHotelOrderMapper itripHotelOrderMapper;
	@Resource
	private ItripTradeEndsMapper itripTradeEndsMapper;
	@Resource
	private SystemConfig systemConfig;
	
	@Override
	public ItripHotelOrder loadItripHotelOrder(String orderNo) throws Exception {
		// TODO Auto-generated method stub
		logger.debug("加载订单："+orderNo);
		Map<String, Object> param=new HashMap();
		param.put("orderNo", orderNo);
		List<ItripHotelOrder> orders=itripHotelOrderMapper.getItripHotelOrderListByMap(param);
		if(orders.size()==1){
			return orders.get(0);
		}else
			return null;
	}

	@Override
	public void paySuccess(String orderNo, int payType,String tradeNo) throws Exception {
		// TODO Auto-generated method stub
		//更新订单状态、支付宝交易号
		logger.debug("订单支付成功："+orderNo);
		ItripHotelOrder itripHotelOrder=this.loadItripHotelOrder(orderNo);
		itripHotelOrder.setOrderStatus(2);//支付成功
		itripHotelOrder.setPayType(payType);
		itripHotelOrder.setTradeNo(tradeNo);//交易号（如支付宝交易号）
		itripHotelOrderMapper.updateItripHotelOrder(itripHotelOrder);
		
		//增加订单后续待处理记录
		ItripTradeEnds itripTradeEnds=new ItripTradeEnds();
		itripTradeEnds.setId(itripHotelOrder.getId());
		itripTradeEnds.setOrderNo(itripHotelOrder.getOrderNo());
		itripTradeEndsMapper.insertItripTradeEnds(itripTradeEnds);
		//通知业务模块后续处理
		sendGet(systemConfig.getTradeEndsUrl(),"orderNo="+orderNo);
	}

	@Override
	public void payFailed(String orderNo, int payType,String tradeNo) throws Exception {
		// TODO Auto-generated method stub
		logger.debug("订单支付失败："+orderNo);
		ItripHotelOrder itripHotelOrder=this.loadItripHotelOrder(orderNo);
		itripHotelOrder.setOrderStatus(1);//支付状态：已取消
		itripHotelOrder.setPayType(payType);
		itripHotelOrder.setTradeNo(tradeNo);//交易号（如支付宝交易号）
		itripHotelOrderMapper.updateItripHotelOrder(itripHotelOrder);
	}

	@Override
	public boolean processed(String orderNo) throws Exception {
		// TODO Auto-generated method stub
		ItripHotelOrder itripHotelOrder=this.loadItripHotelOrder(orderNo);
		return itripHotelOrder.getOrderStatus().equals(2)&&!EmptyUtils.isEmpty(itripHotelOrder.getTradeNo());
	}
	
	 /**
     * 向指定URL发送GET方法的请求
     * 
     * @param url
     *            发送请求的URL
     * @param param
     *            请求参数，请求参数应该是 name1=value1&name2=value2 的形式。
     * 
     */
    @SuppressWarnings("unused")
	public void sendGet(String url, String param) {
    	String result = "";
        BufferedReader in = null;
        try {
            String urlNameString = url + "?" + param;
            URL realUrl = new URL(urlNameString);
            // 打开和URL之间的连接
            URLConnection connection;
            if(systemConfig.getTradeUseProxy()){//代理环境
            	Proxy proxy = new Proxy(Proxy.Type.HTTP,new InetSocketAddress(
            			systemConfig.getTradeProxyHost(), 
            			systemConfig.getTradeProxyPort()));  
            	connection= realUrl.openConnection(proxy);
            }
            else{
            	connection= realUrl.openConnection();
            }
            // 设置通用的请求属性
            connection.setRequestProperty("accept", "*/*");
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            // 建立实际的连接
            connection.connect();           
            System.out.println(connection.getContentLength());
        } catch (Exception e) {
            logger.error("发送GET请求出现异常！" + e);            
            e.printStackTrace();
        }
    }
}
