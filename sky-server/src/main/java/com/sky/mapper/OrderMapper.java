package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param order
     */
    void insert(Orders order);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 条件分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id查询订单
     * @param id
     * @return
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 根据状态统计订单数量
     * @param status
     * @return
     */
    @Select("select count(orders.id) from orders where status = #{status}")
    Integer countStatus(Integer status);

    /**
     * 根据订单状态和订单时间查询订单
     * @param status
     * @param orderTime
     * @return
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    /**
     * 统计营业数据
     * @param map
     * @return
     */
    @Select("select sum(orders.amount) from orders where order_time > #{begin} and order_time < #{end} and status = #{status}")
    Double sumByMap(Map map);

    /**
     * 根据动态条件统计订单数据
     * @param map
     * @return
     */
    Integer getCountOrdersByMap(Map map);

    /**
     * 根据指定时间区间统计top10排名
     * @param beginTime
     * @param endTime
     * @return
     */
    @Select("select od.name,sum(od.number) number from order_detail od,orders os " +
            "where order_time < #{endTime} and order_time > #{beginTime} and od.order_id = os.id and os.status = 5 " +
            "group by od.name order by number desc limit 0,10")
    List<GoodsSalesDTO> getSalesTop(LocalDateTime beginTime, LocalDateTime endTime);
}
