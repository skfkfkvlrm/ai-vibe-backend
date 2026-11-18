package com.oraclejava.demodog.dog;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data JPA 기반 개 서비스. 데이터는 MySQL dogs 테이블에 저장된다. */
@Service
@Transactional(readOnly = true)
public class DogService {

	private final DogRepository dogRepository;

	public DogService(DogRepository dogRepository) {
		this.dogRepository = dogRepository;
	}

	public List<Dog> findAll() {
		return dogRepository.findAllByOrderByIdAsc().stream()
				.map(DogEntity::toDog)
				.toList();
	}

	/** 단건 조회 시 조회수를 1 증가시키고, 증가된 값을 담아 반환한다. */
	@Transactional
	public Optional<Dog> readAndCount(Long id) {
		if (dogRepository.increaseReadcount(id) == 0) {
			return Optional.empty();
		}
		return dogRepository.findById(id).map(DogEntity::toDog);
	}

	@Transactional
	public Dog create(DogRequest request) {
		return dogRepository.saveAndFlush(new DogEntity(request)).toDog();
	}

	@Transactional
	public Optional<Dog> update(Long id, DogRequest request) {
		return dogRepository.findById(id).map(entity -> {
			entity.apply(request);
			return dogRepository.saveAndFlush(entity).toDog();
		});
	}

	@Transactional
	public Optional<Dog> patch(Long id, DogRequest request) {
		return dogRepository.findById(id).map(entity -> {
			entity.applyNonNull(request);
			return dogRepository.saveAndFlush(entity).toDog();
		});
	}

	/** soft delete된 개를 되살린다. 삭제 상태가 아니거나 없는 id면 비어 있다. */
	@Transactional
	public Optional<Dog> restore(Long id) {
		if (dogRepository.restore(id) == 0) {
			return Optional.empty();
		}
		return dogRepository.findById(id).map(DogEntity::toDog);
	}

	/** 행을 지우지 않고 deleted_at만 채운다(엔티티의 SQLDelete). 없거나 이미 삭제된 id면 false. */
	@Transactional
	public boolean delete(Long id) {
		if (!dogRepository.existsById(id)) {
			return false;
		}
		dogRepository.deleteById(id);
		return true;
	}
}
