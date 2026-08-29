update download_job
set status = 'FAILED_FINAL',
    error_code = 'CLEANUP_FAILED',
    error_message = 'Files were manually deleted during disk cleanup',
    failed_at = now(),
    updated_at = now()
where id = '0c5b7513-309a-4f0c-b8fb-cf8f5c8e8079';

update download_job
set status = 'CREATED',
    updated_at = now()
where id = '3fcec006-43b5-457c-8bd4-fba5abd07184'
  and status = 'QUEUED';
