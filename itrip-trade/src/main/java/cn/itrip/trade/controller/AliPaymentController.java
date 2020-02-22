package cn.itrip.trade.controller;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import springfox.documentation.annotations.ApiIgnore;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeWapPayRequest;

import cn.itrip.beans.pojo.ItripHotelOrder;
import cn.itrip.common.EmptyUtils;
import cn.itrip.trade.config.AlipayConfig;
import cn.itrip.trade.service.OrderService;

/**
 * 支付处理控制器
 *
 * @author hduser
 *
 */
@Controller
@RequestMapping("/api")
public class AliPaymentController {

	private Logger logger=Logger.getLogger(AliPaymentController.class);
	@Resource
	private AlipayConfig alipayConfig;
	@Resource
	private OrderService orderService;

	/**
	 * 确认订单信息
	 *
	 * @param id
	 *            订单ID
	 * @return
	 */
	@ApiIgnore
	@RequestMapping(value = "/prepay/{orderNo}", method = RequestMethod.GET)
	public String prePay(@PathVariable String orderNo, ModelMap model) {
		try {
			ItripHotelOrder order = orderService.loadItripHotelOrder(orderNo);

			if (!EmptyUtils.isEmpty(order)) {
				model.addAttribute("hotelName", order.getHotelName());
				model.addAttribute("roomId", order.getRoomId());
				model.addAttribute("count", order.getCount());
				model.addAttribute("payAmount", order.getPayAmount());
				return "pay";
			}else
				return "notfound";
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "error";
		}

	}

	/**
	 * 向支付宝提交支付请求
	 *
	 * @param WIDout_trade_no
	 *            商户订单号，商户网站订单系统中唯一订单号，必填
	 * @param WIDsubject
	 *            订单名称，必填
	 * @param WIDtotal_amount
	 *            付款金额，必填
	 */
	@ApiOperation(value = "订单支付", httpMethod = "POST", 
			protocols = "HTTP", produces = "application/xml", consumes="application/x-www-form-urlencoded",
			response =  String.class, 
			notes = "客户端提交订单支付请求，对该API的返回结果不用处理，浏览器将自动跳转至支付宝。<br><b>请使用普通表单提交，不能使用ajax异步提交。</b>")	
	@RequestMapping(value = "/pay", method = RequestMethod.POST,produces="application/xml", consumes="application/x-www-form-urlencoded")
	public void pay(
			@ApiParam(name="WIDout_trade_no",value="订单编号",required=true)
			@RequestParam String WIDout_trade_no, 
			@ApiParam(name="WIDsubject",value="订单名称",required=true)
			@RequestParam String WIDsubject,
			@ApiParam(name="WIDtotal_amount",value="订单金额",required=true)
			@RequestParam String WIDtotal_amount, HttpServletResponse response) {
		// 超时时间 可空
		String timeout_express = "2m";
		// 销售产品码 必填
		String product_code = "QUICK_WAP_PAY";
		/**********************/
		// SDK 公共请求类，包含公共请求参数，以及封装了签名与验签，开发者无需关注签名与验签
		// 调用RSA签名方式
		AlipayClient client = new DefaultAlipayClient(alipayConfig.getUrl(),
				alipayConfig.getAppID(), alipayConfig.getRsaPrivateKey(),
				alipayConfig.getFormat(), alipayConfig.getCharset(),
				alipayConfig.getAlipayPublicKey(), alipayConfig.getSignType());
		AlipayTradeWapPayRequest alipay_request = new AlipayTradeWapPayRequest();

		// 封装请求支付信息
		AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
		model.setOutTradeNo(WIDout_trade_no);
		model.setSubject(WIDsubject);
		model.setTotalAmount(WIDtotal_amount);
		model.setTimeoutExpress(timeout_express);
		model.setProductCode(product_code);
		alipay_request.setBizModel(model);
		// 设置异步通知地址
		alipay_request.setNotifyUrl(alipayConfig.getNotifyUrl());
		// 设置同步地址
		alipay_request.setReturnUrl(alipayConfig.getReturnUrl());
		// form表单生产
		String form = "";
		try {
			// 调用SDK生成表单
			form = client.pageExecute(alipay_request).getBody();
			System.out.println(form);
			response.setContentType("text/html;charset="
					+ alipayConfig.getCharset());
			response.getWriter().write(form);// 直接将完整的表单html输出到页面
			response.getWriter().flush();
			response.getWriter().close();
		} catch (AlipayApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 导步通知，跟踪支付状态变更
	 */
	@ApiIgnore
	@RequestMapping(value = "/notify", method = RequestMethod.POST)
	public void trackPaymentStatus(HttpServletRequest request,
			HttpServletResponse response) {
		try {
			// 获取支付宝POST过来反馈信息
			Map<String, String> params = new HashMap<String, String>();
			Map requestParams = request.getParameterMap();
			for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
				String name = (String) iter.next();
				String[] values = (String[]) requestParams.get(name);
				String valueStr = "";
				for (int i = 0; i < values.length; i++) {
					valueStr = (i == values.length - 1) ? valueStr + values[i]
							: valueStr + values[i] + ",";
				}
				// 乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
				// valueStr = new String(valueStr.getBytes("ISO-8859-1"), "gbk");
				params.put(name, valueStr);
			}
			// 获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以下仅供参考)//
			// 商户订单号
			String out_trade_no = new String(request.getParameter("out_trade_no")
					.getBytes("ISO-8859-1"), "UTF-8");
			// 支付宝交易号
			String trade_no = new String(request.getParameter("trade_no").getBytes(
					"ISO-8859-1"), "UTF-8");
			// 交易状态
			String trade_status = new String(request.getParameter("trade_status")
					.getBytes("ISO-8859-1"), "UTF-8");

			// 获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以上仅供参考)//
			// 计算得出通知验证结果
			// boolean AlipaySignature.rsaCheckV1(Map<String, String> params, String
			// publicKey, String charset, String sign_type)
			boolean verify_result = AlipaySignature.rsaCheckV1(params,
					alipayConfig.getAlipayPublicKey(), alipayConfig.getCharset(), "RSA2");

			if (verify_result) {// 验证成功
				// ////////////////////////////////////////////////////////////////////////////////////////
				// 请在这里加上商户的业务逻辑程序代码		

				//即时到账普通版，那么这时的交易状态值为：  TRADE_FINISHED；如果是即时到账高级版，此时的交易状态值就为：TRADE_SUCCESS	
				//收到TRADE_FINISHED请求后，这笔订单就结束了，支付宝不会再主动请求商户网站了；收到TRADE_SUCCESS请求后，后续一定还有至少一条通知记录，即TRADE_FINISHED。
				if (trade_status.equals("TRADE_FINISHED")) {
					// 判断该笔订单是否在商户网站中已经做过处理
					// 如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
					// 请务必判断请求时的total_fee、seller_id与通知时获取的total_fee、seller_id为一致的
					// 如果有做过处理，不执行商户的业务程序
					if(!orderService.processed(out_trade_no))
					{
						orderService.paySuccess(out_trade_no, 2,trade_no);
					}
					logger.info("订单："+out_trade_no+" 交易完成");
					// 注意：
					// 如果签约的是可退款协议，退款日期超过可退款期限后（如三个月可退款），支付宝系统发送该交易状态通知
					// 如果没有签约可退款协议，那么付款完成后，支付宝系统发送该交易状态通知。
				} else if (trade_status.equals("TRADE_SUCCESS")) {
					// 判断该笔订单是否在商户网站中已经做过处理
					// 如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
					// 请务必判断请求时的total_fee、seller_id与通知时获取的total_fee、seller_id为一致的
					// 如果有做过处理，不执行商户的业务程序
					if(!orderService.processed(out_trade_no))
					{
						orderService.paySuccess(out_trade_no, 2,trade_no);
					}
					logger.info("订单："+out_trade_no+" 交易成功");
								
					// 注意：
					// 如果签约的是可退款协议，那么付款完成后，支付宝系统发送该交易状态通知。
				}
				
				response.getWriter().println("success"); // 请不要修改或删除

				// ////////////////////////////////////////////////////////////////////////////////////////
			} else {// 验证失败
				orderService.payFailed(out_trade_no, 1,trade_no);
				response.getWriter().println("fail");
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(e.getMessage());			
		} catch (AlipayApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(e.getMessage());
		}
		
	}

	/**
	 * 支付宝页面跳转同步通知页面
	 */
	@ApiIgnore
	@RequestMapping(value = "/return", method = RequestMethod.GET)
	public void callback(HttpServletRequest request,
			HttpServletResponse response) {
		try {
			//获取支付宝GET过来反馈信息
			Map<String,String> params = new HashMap<String,String>();
			Map requestParams = request.getParameterMap();
			for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
				String name = (String) iter.next();
				String[] values = (String[]) requestParams.get(name);
				String valueStr = "";
				for (int i = 0; i < values.length; i++) {
					valueStr = (i == values.length - 1) ? valueStr + values[i]
							: valueStr + values[i] + ",";
				}
				//乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
				valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
				params.put(name, valueStr);
			}
			
			//获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以下仅供参考)//
			//商户订单号

			String out_trade_no = new String(request.getParameter("out_trade_no").getBytes("ISO-8859-1"),"UTF-8");

			//支付宝交易号

			String trade_no = new String(request.getParameter("trade_no").getBytes("ISO-8859-1"),"UTF-8");

			//获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以上仅供参考)//
			//计算得出通知验证结果
			//boolean AlipaySignature.rsaCheckV1(Map<String, String> params, String publicKey, String charset, String sign_type)
			boolean verify_result = AlipaySignature.rsaCheckV1(params, alipayConfig.getAlipayPublicKey(), alipayConfig.getCharset(), "RSA2");
			
			if(verify_result){//验证成功				
				String id=orderService.loadItripHotelOrder(out_trade_no).getId().toString();
				//提示支付成功
				response.sendRedirect(
						String.format(alipayConfig.getPaymentSuccessUrl(), out_trade_no,id));
			}else{				
				//提示支付失败
				response.sendRedirect(alipayConfig.getPaymentFailureUrl());
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (AlipayApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}
}
