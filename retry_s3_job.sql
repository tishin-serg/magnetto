begin;

update download_file
set status = 'UPLOAD_FAILED_RETRYABLE',
    updated_at = now()
where job_id = '85e2b7f2-7af4-487a-a737-a47ccbb5c0ae'::uuid
  and status in ('UPLOADING_TO_S3', 'UPLOADING');

update download_job
set status = 'RETRY_WAITING',
    resume_status = 'UPLOADING_TO_S3',
    next_retry_at = now() - interval '1 second',
    updated_at = now()
where id = '85e2b7f2-7af4-487a-a737-a47ccbb5c0ae'::uuid;

commit;

select id, status, resume_status, retry_count, next_retry_at, updated_at
from download_job
where id = '85e2b7f2-7af4-487a-a737-a47ccbb5c0ae'::uuid;

select file_name, status, upload_attempts, error_code, error_message, updated_at
from download_file
where job_id = '85e2b7f2-7af4-487a-a737-a47ccbb5c0ae'::uuid;
