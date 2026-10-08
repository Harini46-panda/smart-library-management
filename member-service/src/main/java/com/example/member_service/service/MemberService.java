package com.example.member_service.service;

import com.example.member_service.dto.MemberRequest;
import com.example.member_service.entity.Member;
import com.example.member_service.exception.DuplicateMemberException;
import com.example.member_service.exception.MemberNotFoundException;
import com.example.member_service.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MemberService {

    private final MemberRepository repository;

    public MemberService(MemberRepository repository) {
        this.repository = repository;
    }

    public Member create(MemberRequest request) {

        if (repository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateMemberException(request.getEmail());
        }

        Member member = new Member();
        member.setName(request.getName());
        member.setEmail(request.getEmail());
        member.setPhone(request.getPhone());
        member.setMembershipDate(LocalDate.now());
        member.setActive(true);

        return repository.save(member);
    }

    public List<Member> getAll() {
        return repository.findAll();
    }

    public Member getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(id));
    }
}
