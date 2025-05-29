package org.L2.music.domain.service;

import org.L2.music.infrastructure.SongMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SongService {
    @Autowired
    private SongMapper songMapper;
}
