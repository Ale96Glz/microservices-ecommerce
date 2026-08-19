package com.aosorio.ecommerce.auth.service;

import com.aosorio.ecommerce.auth.domain.Usuario;
import com.aosorio.ecommerce.auth.dto.PageResponseDTO;
import com.aosorio.ecommerce.auth.dto.UsuarioRequestDTO;
import com.aosorio.ecommerce.auth.dto.UsuarioResponseDTO;
import com.aosorio.ecommerce.auth.exception.ResourceInUseException;
import com.aosorio.ecommerce.auth.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.auth.mapper.UsuarioMapper;
import com.aosorio.ecommerce.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;

    @Override
    @Transactional
    public UsuarioResponseDTO actualizar(Long id, UsuarioRequestDTO request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con id: " + id));

        if (usuarioRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new ResourceInUseException("Ya existe un usuario con el email: " + request.getEmail());
        }

        usuario.setNombre(request.getNombre());
        usuario.setEmail(request.getEmail());
        Usuario actualizado = usuarioRepository.save(usuario);
        log.info("Se ha actualizado el usuario: {}", actualizado.getId());
        return usuarioMapper.toResponseDto(actualizado);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con id: " + id));
        usuarioRepository.delete(usuario);
        log.info("Se ha eliminado el usuario: {}", usuario.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con id: " + id));
        return usuarioMapper.toResponseDto(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> obtenerTodos() {
        return usuarioRepository.findAll().stream()
                .map(usuarioMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<UsuarioResponseDTO> obtenerTodosPaginado(Pageable pageable) {
        return PageResponseDTO.from(
                usuarioRepository.findAll(pageable).map(usuarioMapper::toResponseDto)
        );
    }
}
