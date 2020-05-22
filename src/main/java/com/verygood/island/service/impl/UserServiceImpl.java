package com.verygood.island.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.verygood.island.constant.Constants;
import com.verygood.island.entity.Stamp;
import com.verygood.island.entity.User;
import com.verygood.island.exception.bizException.BizException;
import com.verygood.island.mapper.UserMapper;
import com.verygood.island.service.StampService;
import com.verygood.island.service.UserService;
import com.verygood.island.util.ImageUtils;
import com.verygood.island.util.Md5Util;
import com.verygood.island.util.UploadUtils;
import com.verygood.island.util.LocationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;

/**
 * <p>
 * 用户 服务实现类
 * </p>
 *
 * @author chaos
 * @since 2020-05-04
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {


    @Resource
    LocationUtils locationUtils;

    @Resource
    StampService stampService;

    /**
     * 用户名的正则表达式：4-16位的字母数字组合（可包含其中一种）
     */
    private final String NAME_PATTERN = "^[a-zA-Z0-9]{4,16}$";

    /**
     * 密码的正则表达式：6-16位的字母数字组合（必须包含字母，数字）
     */
    private final String PASSWORD_PATTERN = "^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{6,16}$";

    /**
     * 根据id查询User
     *
     * @param user 登陆用户
     * @return 返回登陆成功的用户
     * @author chaos
     * @since 2020-05-02
     */
    @Override
    public User login(User user) {
        log.info("正在执行登录操作：user:【{}】", user);
        if (StringUtils.isEmpty(user.getUsername()) || StringUtils.isEmpty(user.getPassword())){
            log.info("执行登录操作时未传输账号或者密码");
            throw new BizException("请输入正确的账号或者密码");
        }
        // 查询数据库中该账户的账号密码
        User userPo = super.getOne(new QueryWrapper<User>().eq("username", user.getUsername()));
        if (userPo == null){
            log.info("执行登录操作时传输了错误的账号：[{}]", user.getUsername());
            throw new BizException("该账号未进行注册");
        }
        // 校验密码
        if (!userPo.getPassword().equals(Md5Util.getMd5String(user.getPassword()))){
            log.info("执行登录操作时密码错误，账号信息为：【{}】", user);
            throw new BizException("密码错误！");
        }

        // 密码进行隐藏
        userPo.setPassword("");

        return userPo;
    }

    @Override
    public Page<User> listUsersByPage(int page, int pageSize, String factor) {
        log.info("正在执行分页查询user: page = {} pageSize = {} factor = {}", page, pageSize, factor);
        QueryWrapper<User> queryWrapper = new QueryWrapper<User>().like("", factor);
        //TODO 这里需要自定义用于匹配的字段,并把wrapper传入下面的page方法
        Page<User> result = super.page(new Page<>(page, pageSize));
        log.info("分页查询user完毕: 结果数 = {} ", result.getRecords().size());
        return result;
    }

    @Override
    public User getUserById(int id) {
        log.info("正在查询user中id为{}的数据", id);
        User user = super.getById(id);
        log.info("查询id为{}的user{}", id, (null == user ? "无结果" : "成功"));
        return user;
    }

    @Override
    public int insertUser(User user) {
        log.info("正在插入user");

        // 校验账号和密码参数
        if(StringUtils.isEmpty(user.getUsername()) || StringUtils.isEmpty(user.getPassword())){
            log.info("插入用户时传输空参数：账号或者密码为空");
            throw new BizException("账号或者密码不能为空");
        }

        // 查看账户格式是否正确
        if (!user.getUsername().matches(NAME_PATTERN)){
            log.info("用户进行注册时未传输正确的用户名格式：【{}】", user.getUsername());
            throw new BizException("用户名格式错误！应为：4-16位的字母数字组合");
        }

        if (!user.getPassword().matches(PASSWORD_PATTERN)){
            log.info("用户进行注册时未传输正确的密码格式：【{}】", user.getPassword());
            throw new BizException("密码格式错误！应为：6-16位的字母数字组合");
        }

        // 查看该用户是否已经存在
        if (super.getOne(new QueryWrapper<User>().eq("username", user.getUsername())) != null){
            log.info("插入数据时检测到账号【{}】已经存在！插入失败", user.getUsername());
            throw new BizException("该账号已经存在");
        }

        // 对密码进行加密
        user.setPassword(Md5Util.getMd5String(user.getPassword()));

        // 进行插库操作
        if (super.save(user)) {
            log.info("插入user成功,id为{}", user.getUserId());
        } else {
            log.error("插入user失败");
            throw new BizException("添加失败");
        }

        // 进行邮票的增加,初始化送 5 张 “中国” 类型邮票
        for (int i = 0; i < Constants.INIT_STAMP_NUMBER; i++){
            Stamp stamp = new Stamp();
            stamp.setStampName(Constants.STAMP_CHINA);
            stamp.setUserId(user.getUserId());
            stampService.insertStamp(stamp);
        }

        return user.getUserId();
    }

    @Override
    public int deleteUserById(int id) {
        log.info("正在删除id为{}的user", id);
        if (super.removeById(id)) {
            log.info("删除id为{}的user成功", id);
            return id;
        } else {
            log.error("删除id为{}的user失败", id);
            throw new BizException("删除失败[id=" + id + "]");
        }
    }

    @Override
    public int updateUser(User user) {
        log.info("正在更新id为{}的user", user.getUserId());
        user.setWord(null);
        user.setPhoto(null);
        user.setSendLetter(null);
        user.setReceiveLetter(null);
        locationUtils.isValidLocation(user.getCity());
        if (super.updateById(user)) {
            log.info("更新d为{}的user成功", user.getUserId());
            return user.getUserId();
        } else {
            log.error("更新id为{}的user失败", user.getUserId());
            throw new BizException("更新失败[id=" + user.getUserId() + "]");
        }
    }

    @Override
    public String uploadIcon(MultipartFile file, Integer userId) {

        log.info("正在执行上传用户头像操作");
        if (file == null || file.getSize() == 0){
            log.info("上传的文件为空！");
            throw new BizException("请选择正确的文件！");
        }

        // 对上传的文件进行判断
        boolean isImage = false;
        try {
            isImage = ImageUtils.isImage(file.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!isImage){
            log.info("用户上传的文件不是图片：【{}】", file.getOriginalFilename());
            throw new BizException("上传头像失败！该文件非图片类型");
        }

        log.info("上传的文件名称：【{}】", file.getOriginalFilename());
        File result = UploadUtils.upload(file, UploadUtils.getFileName(file.getOriginalFilename()));

        // 进行数据库的更新
        User userPo = getOne(new QueryWrapper<User>().eq("user_id", userId));

        // 删除旧的头像地址
        if (!StringUtils.isEmpty(userPo.getPhoto())){
            UploadUtils.deleteFile(userPo.getPhoto());
        }

        // 设置图片地址
        userPo.setPhoto(result.getName());

        updateById(userPo);

        return result.getName();
    }
}
