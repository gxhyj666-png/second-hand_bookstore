package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.textbook.loop.common.BizException;
import com.textbook.loop.domain.BizPickupPoint;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.mapper.BizPickupPointMapper;
import com.textbook.loop.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自提点：列表（可带经纬度算距离）与详情。
 */
@Service
public class PickupService {

    private final BizPickupPointMapper pointMapper;
    private final SysUserMapper userMapper;

    public PickupService(BizPickupPointMapper pointMapper, SysUserMapper userMapper) {
        this.pointMapper = pointMapper;
        this.userMapper = userMapper;
    }

    public List<Map<String, Object>> list(Long schoolId, Double lat, Double lng) {
        List<BizPickupPoint> points = pointMapper.selectList(new LambdaQueryWrapper<BizPickupPoint>()
                .eq(schoolId != null, BizPickupPoint::getSchoolId, schoolId)
                .eq(BizPickupPoint::getStatus, "ENABLED")
                .orderByAsc(BizPickupPoint::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BizPickupPoint p : points) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("address", p.getAddress());
            m.put("openTime", p.getOpenTime());
            m.put("closeTime", p.getCloseTime());
            m.put("capacity", p.getCapacity());
            m.put("latitude", p.getLatitude());
            m.put("longitude", p.getLongitude());
            if (lat != null && lng != null && p.getLatitude() != null && p.getLongitude() != null) {
                m.put("distance", Math.round(distanceMeters(lat, lng,
                        p.getLatitude().doubleValue(), p.getLongitude().doubleValue())));
            } else {
                m.put("distance", null);
            }
            result.add(m);
        }
        return result;
    }

    public BizPickupPoint get(Long id) {
        BizPickupPoint p = pointMapper.selectById(id);
        if (p == null) {
            throw BizException.notFound("自提点不存在");
        }
        return p;
    }

    /** 店员所属自提点（用于核销台展示）。 */
    public List<BizPickupPoint> pointsOfManager(Long managerId) {
        SysUser user = userMapper.selectById(managerId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return pointMapper.selectList(new LambdaQueryWrapper<BizPickupPoint>()
                .eq(BizPickupPoint::getManagerId, managerId));
    }

    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
