package org.usersystem.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import org.usersystem.dao.AppointmentDao;
import org.usersystem.enums.ResultCodeEnum;
import org.usersystem.pojo.AppointmentHistoryInfo;
import org.usersystem.pojo.AppointmentInfo;
import org.usersystem.pojo.DoctorWorkInfo;
import org.usersystem.opt.GetUUID;
import org.usersystem.opt.ResponseHelper;
import org.usersystem.opt.ResponseV2;
import org.usersystem.opt.TimeOpt;
import org.usersystem.service.CaseService;
import org.usersystem.vo.request.FeedBackRequest;

import javax.annotation.Resource;
import java.util.*;

@Api(tags = "用户预约Api")
@RestController
@CrossOrigin
@RequestMapping(value = "/api")
public class AppointmentControl {

    @Resource
    private AppointmentDao appointmentDao;



    //预约时间表
    private static String[] morningTimes = {"", " 08:00:00", " 09:00:00", " 10:00:00"};
    private static String[] afternoonTimes = {"", " 13:00:00", " 14:00:00", " 15:00:00"};

    //录入预约信息，根据value可以匹配的到前端发送http的代码，之后同理
    @ApiOperation("录入预约信息")
    @RequestMapping(value = "/appointment/add", method = RequestMethod.POST)
    public ResponseV2 addAppointment(@RequestBody JSONObject jsonObject) {
        //从前端获取的数据json格式
        System.out.println(jsonObject);
        String userId = jsonObject.getString("userId");//获得用户id
        String type = jsonObject.getString("type");//约上午还是下午
        int num = jsonObject.getInteger("num");//他是第几个预约的
        String staffId = jsonObject.getString("staffId");//要预约的医生的id
        String date = jsonObject.getString("date");//预约的时间

        List<String> appointList = null;
        try {
            appointList = appointmentDao.getAppointmentNum(userId, date);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseHelper.create(500, "预约失败!");
        }

        if (appointList.size() > 1) {
            return ResponseHelper.create(201, "预约失败!每人每天只能预约一次！");
        }

        AppointmentInfo appointmentInfo = new AppointmentInfo();
        String appointmentId = GetUUID.getUUID();
        String createTime = TimeOpt.getCurrentTime();

        appointmentInfo.setAppointmentId(appointmentId);
        appointmentInfo.setUserId(userId);
        appointmentInfo.setStaffId(staffId);
        appointmentInfo.setStatus("WAIT");
        appointmentInfo.setCreateTime(createTime);

        String appointmentTime = "";

        //判断是上午还是下午预约
        if ("morning".equals(type)) {
            appointmentTime = date + morningTimes[num];
        } else {
            appointmentTime = date + afternoonTimes[num];
        }
        appointmentInfo.setAppointmentTime(appointmentTime);

        try {
            //插入信息
            appointmentDao.addAppointmentInfo(appointmentInfo);
            return ResponseHelper.create(200, "预约成功!");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseHelper.create(500, "预约失败!");
        }
    }

    //查看一段时间内的历史预约记录
    @ApiOperation("查看一段时间内的历史预约记录")
    @RequestMapping(value = "/appointment/history")
    public ResponseV2 returnReserveById(@RequestBody JSONObject jsonObject) {
        System.out.println(jsonObject);
        String userId = jsonObject.getString("userId");
        try {
            List<AppointmentHistoryInfo> appointmentHistoryList = appointmentDao.searchReserveHistory(userId);
            System.out.println(appointmentHistoryList);
            return ResponseHelper.create(appointmentHistoryList, 200, "预约历史查询成功");
        } catch (Exception e) {
            return ResponseHelper.create(500, "预约历史查询失败");
        }
    }

    //取消预约消息
    @ApiOperation("取消预约消息")
    @RequestMapping(value = "/appointment/delete", method = RequestMethod.POST)
    public ResponseV2 cancelReserve(@RequestBody JSONObject jsonObject) {
        System.out.println(jsonObject);

        //获取前端的数据
        String appointmentId = jsonObject.getString("appointmentId");
        try {
            appointmentDao.deleteAppointment(appointmentId);
            return ResponseHelper.create(null, 200, "删除成功");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseHelper.create(500, "删除失败");
        }

    }

    //查询医生7天内预约情况
    @ApiOperation("查询医生7天内预约情况")
    @RequestMapping(value = "/get/staffWorkScheduleList", method = RequestMethod.POST)
    @ResponseBody
    public ResponseV2 getDoctorWorkTime(@RequestBody JSONObject jsonObject) {
        System.out.println(jsonObject.toJSONString());
        String staffId = (String) jsonObject.get("staffId");

        List<String> doctorAppointmentTimeList;//这个医生已经预约的时间

        Map<String, String> paraMap = new HashMap<>();
        String startTime = TimeOpt.getCurrentTime().split(" ")[0];
        ;
        String endTimeTime = TimeOpt.getFetureDate(7).split(" ")[0];
        paraMap.put("staffId", staffId);
        paraMap.put("startTime", startTime);
        paraMap.put("endTimeTime", endTimeTime);
        doctorAppointmentTimeList = appointmentDao.getWorkTimeSevenDays(paraMap);
        System.out.println(doctorAppointmentTimeList);
        Map<String, DoctorWorkInfo> workInfoMap = new HashMap<>();
        for (String appointmentTime : doctorAppointmentTimeList) {
            String date = appointmentTime.split(" ")[0];
            if (workInfoMap.containsKey(date)) {
                DoctorWorkInfo workInfo = workInfoMap.get(date);
                if (TimeOpt.moringOrAfterNoon(appointmentTime) > 0) {
                    int morningNum = workInfo.getMorningNum() + 1;
                    workInfo.setMorningNum(morningNum);
                } else {
                    int afternoonNum = workInfo.getAfternoonNum() + 1;
                    workInfo.setAfternoonNum(afternoonNum);
                }
                workInfoMap.replace(date, workInfo);
            } else {
                DoctorWorkInfo workInfo = new DoctorWorkInfo();
                workInfo.setAppointmentTime(date);
                if (TimeOpt.moringOrAfterNoon(appointmentTime) > 0) {
                    int morningNum = workInfo.getMorningNum() + 1;
                    workInfo.setMorningNum(morningNum);
                } else {
                    int afternoonNum = workInfo.getAfternoonNum() + 1;
                    workInfo.setAfternoonNum(afternoonNum);
                }
                workInfoMap.put(date, workInfo);
            }
        }

        List<DoctorWorkInfo> workList = new ArrayList<>();

        for (Map.Entry<String, DoctorWorkInfo> entry : workInfoMap.entrySet()) {
            workList.add(entry.getValue());
        }

        for (int i = 0; i < 6; i++) {
            String date = TimeOpt.getFetureDate(i).split(" ")[0];
            int size = workList.size();
            boolean flag = false;
            for (int j = 0; j < size; j++) {
                if (workList.get(j).getAppointmentTime().equals(date)) {
                    flag = true;
                }
            }
            if (!flag) {
                DoctorWorkInfo workInfo = new DoctorWorkInfo();
                workInfo.setAppointmentTime(date);
                workList.add(workInfo);
            }
        }
        return ResponseHelper.create(workList, 200, "医生上班情况查询成功");
    }


}
