package com.oraclejava.hanabank;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** http://localhost:8080/hanabank 를 React 빌드(index.html)로 연결한다. */
@Controller
public class SpaController {
    @GetMapping({"/hanabank", "/hanabank/"})
    public String hanabank() {
        return "forward:/index.html";
    }
}
