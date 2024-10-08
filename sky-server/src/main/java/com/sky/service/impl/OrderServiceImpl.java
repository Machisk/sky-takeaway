package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;
//    @Value("${sky.shop.address}")
//    private String shopAddress;
//
//    @Value("${sky.baidu.ak}")
//    private String ak;
//
//    /**
//     * 检查客户的收货地址是否超出配送范围
//     * @param address
//     */
//    private void checkOutOfRange(String address) {
//        Map map = new HashMap();
//        map.put("address",shopAddress);
//        map.put("output","json");
//        map.put("ak",ak);
//
//        //获取店铺的经纬度坐标
//        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);
//
//        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
//        if(!jsonObject.getString("status").equals("0")){
//            throw new OrderBusinessException("店铺地址解析失败");
//        }
//
//        //数据解析
//        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
//        String lat = location.getString("lat");
//        String lng = location.getString("lng");
//        //店铺经纬度坐标
//        String shopLngLat = lat + "," + lng;
//
//        map.put("address",address);
//        //获取用户收货地址的经纬度坐标
//        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);
//
//        jsonObject = JSON.parseObject(userCoordinate);
//        if(!jsonObject.getString("status").equals("0")){
//            throw new OrderBusinessException("收货地址解析失败");
//        }
//
//        //数据解析
//        location = jsonObject.getJSONObject("result").getJSONObject("location");
//        lat = location.getString("lat");
//        lng = location.getString("lng");
//        //用户收货地址经纬度坐标
//        String userLngLat = lat + "," + lng;
//
//        map.put("origin",shopLngLat);
//        map.put("destination",userLngLat);
//        map.put("steps_info","0");
//
//        //路线规划
//        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);
//
//        jsonObject = JSON.parseObject(json);
//        if(!jsonObject.getString("status").equals("0")){
//            throw new OrderBusinessException("配送路线规划失败");
//        }
//
//        //数据解析
//        JSONObject result = jsonObject.getJSONObject("result");
//        JSONArray jsonArray = (JSONArray) result.get("routes");
//        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");
//
//        if(distance > 5000){
//            //配送距离超过5000米
//            throw new OrderBusinessException("超出配送范围");
//        }
//    }


    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //异常情况的处理（收货地址为空、购物车为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
//
//        //检查用户地址是否超出配送范围
//        checkOutOfRange(addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail());

        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);

        //查询当前用户的购物车数据
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.shoppingCartList(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,order);
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setUserId(userId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());

        //向订单表插入1条数据
        orderMapper.insert(order);

        //订单明细数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }

        //向明细表插入n条数据
        orderDetailMapper.insertBatch(orderDetailList);

        //清理购物车中的数据
        shoppingCartMapper.deleteByUserId(userId);

        //封装返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
        //通过websocket向客户端浏览器推送消息type orderId content
        Map map = new HashMap();
        map.put("type", 1);//1表示来单提醒，2表示客户催单
        map.put("orderId", ordersDB.getId());
        map.put("content","订单号" + outTradeNo);

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 历史订单查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @Transactional
    @Override
    public PageResult pageQueryOrders(int page, int pageSize, Integer status) {
        //传参调用分页函数进行分页查询
        PageHelper.startPage(page, pageSize);

        //传DTO进行条件查询，传给pageQuery
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());

        Page<Orders> pageQuery = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();

        if (pageQuery != null && pageQuery.getTotal() > 0) {
            for (Orders orders : pageQuery) {
                //根据orderId查询订单详细信息
                Long orderId = orders.getId();
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);

                //属性拷贝得到订单信息，将查询到的订单详细信息放入VO对象中
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);

                //将每次得到的VO放入最后的list中
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(pageQuery.getTotal(),orderVOList);
    }

    /**
     * 根据id查询订单详情
     * @param id
     * @return
     */
    @Transactional
    @Override
    public OrderVO getDetailById(Long id) {
        Orders orders = orderMapper.getById(id);

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 取消订单
     * @param id
     * @throws Exception
     */
    @Override
    public void cancelOrderById(Long id) throws Exception {
        //根据id查询订单
        Orders orders = orderMapper.getById(id);

        //判断订单是否存在
        if (orders == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //如果订单状态不是待支付或待接单，则需要联系商家
        Integer status = orders.getStatus();
        if (status > 2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orderss = new Orders();
        orderss.setId(orders.getId());

        //待接单状态下需要退款
        if (orderss.equals(Orders.TO_BE_CONFIRMED)){
            weChatPayUtil.refund(
                    orders.getNumber(),//商户订单号
                    orders.getNumber(),//商户退款单号
                    new BigDecimal(String.valueOf(orders.getAmount())),//退款金额
                    new BigDecimal(String.valueOf(orders.getAmount())));//原订单金额
            orderss.setStatus(Orders.REFUND);
        }
        orderss.setOrderTime(LocalDateTime.now());
        orderss.setCancelReason("用户取消");
        orderss.setStatus(Orders.CANCELLED);
        orderMapper.update(orderss);
    }

    /**
     * 再来一单
     * @param id
     */
    @Transactional
    @Override
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    @Transactional
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(),orderVOList);
    }

    /**
     * 返回订单菜品信息，通过VO响应结果
     * @param page
     * @return
     */
    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        List<OrderVO> orderVOList = new ArrayList<>();
        List<Orders> result = page.getResult();
        if (result.size() > 0) {
            for (Orders orders : result) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);
                orderVO.setOrderDishes(orderDishes);

                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());
        return String.join("",orderDishList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);//待接单
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);//待派送
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setConfirmed(toBeConfirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        orderStatisticsVO.setConfirmed(confirmed);
        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {

        Orders orderById = orderMapper.getById(ordersRejectionDTO.getId());
        Integer status = orderById.getStatus();
        if (!status.equals(Orders.TO_BE_CONFIRMED) || orderById != null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Integer payStatus = orderById.getPayStatus();
        if (payStatus == Orders.PAID){
            String refund = weChatPayUtil.refund(
                    orderById.getNumber(),
                    orderById.getNumber(),
                    new BigDecimal(String.valueOf(orderById.getAmount())),
                    new BigDecimal(String.valueOf(orderById.getAmount())));
            log.info("申请退款：{}",refund);
        }

        Orders orders = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    @Transactional
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {

        Orders order = orderMapper.getById(ordersCancelDTO.getId());

        if (order.getPayStatus().equals(Orders.PAID)){
            String refund = weChatPayUtil.refund(
                    order.getNumber(),
                    order.getNumber(),
                    new BigDecimal(String.valueOf(order.getAmount())),
                    new BigDecimal(String.valueOf(order.getAmount())));
            log.info("申请退款：{}",refund);
        }

        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .cancelReason(ordersCancelDTO.getCancelReason())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 派送订单
     * @param id
     */
    @Transactional
    @Override
    public void delivery(Long id) {
        Orders order = orderMapper.getById(id);
        Integer status = order.getStatus();
        if (order != null || !status.equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(order.getId())
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 完成订单
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders order = orderMapper.getById(id);
        Integer status = order.getStatus();
        if (order != null || !status.equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(order.getId())
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 客户催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders order = orderMapper.getById(id);

        Integer status = order.getStatus();
        if (order != null ){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Map map = new HashMap();
        map.put("type",2);//2表示客户催单
        map.put("orderId",id);
        map.put("content","订单号"+order.getId());

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);

    }
}
