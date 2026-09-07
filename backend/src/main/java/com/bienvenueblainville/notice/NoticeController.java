package com.bienvenueblainville.notice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final SpecialNoticeService service;

    public NoticeController(SpecialNoticeService service) {
        this.service = service;
    }

    @GetMapping("/active")
    public List<SpecialNotice> active() {
        return service.active();
    }
}
