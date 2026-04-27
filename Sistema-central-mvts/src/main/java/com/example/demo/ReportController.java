/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.example.demo;

import com.example.demo.model.PosicionVehiculo;
import com.example.demo.repository.PosicionRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author SDavidLedesma
 */
@RestController
@RequestMapping("/api/reportes")
public class ReportController {

    @Autowired
    private PosicionRepository repository;

    @GetMapping("/ultima-posicion")
    public List<PosicionVehiculo> obtenerUltimas() {
        return repository.findAll();
    }
}
