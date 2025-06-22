package org.L2.music.domain.service;


import org.L2.music.infrastructure.SingerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SingerService {
    @Autowired
    private SingerMapper singerMapper;
}
